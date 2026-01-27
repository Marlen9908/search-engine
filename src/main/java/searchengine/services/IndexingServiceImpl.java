package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.Response;
import searchengine.model.*;
import searchengine.repositories.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
@Slf4j
public class IndexingServiceImpl implements IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final SitesList sitesList;
    private final LemmaFinder lemmaFinder;

    private final Map<String, ForkJoinPool> indexingPools = new ConcurrentHashMap<>();
    private final Map<String, SiteIndexer> siteIndexers = new ConcurrentHashMap<>();
    private volatile boolean isIndexing = false;

    @Override
    public Response startIndexing() {
        if (isIndexing) {
            return new Response(false, "Индексация уже запущена");
        }

        isIndexing = true;

        new Thread(() -> {
            for (Site siteConfig : sitesList.getSites()) {
                indexSite(siteConfig);
            }
        }).start();

        return new Response(true);
    }

    private void indexSite(Site siteConfig) {
        SiteEntity siteEntity = null;
        try {
            // Удаляем старые данные
            siteRepository.findByUrl(siteConfig.getUrl()).ifPresent(site -> {
                log.info("Deleting old data for site: {}", siteConfig.getUrl());
                siteRepository.delete(site);
            });

            // Создаем новую запись сайта
            siteEntity = new SiteEntity();
            siteEntity.setStatus(IndexingStatus.INDEXING);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteEntity.setUrl(siteConfig.getUrl());
            siteEntity.setName(siteConfig.getName());
            siteEntity = siteRepository.save(siteEntity);
            siteRepository.flush(); // Принудительная запись в БД

            log.info("Started indexing site: {} ({})", siteEntity.getName(), siteEntity.getUrl());

            // Запускаем обход сайта
            Set<String> visitedUrls = ConcurrentHashMap.newKeySet();
            SiteIndexer indexer = new SiteIndexer(
                    siteConfig.getUrl(), siteEntity, pageRepository, visitedUrls, sitesList
            );

            siteIndexers.put(siteConfig.getUrl(), indexer);
            ForkJoinPool pool = new ForkJoinPool();
            indexingPools.put(siteConfig.getUrl(), pool);

            // Создаем поток для обновления status_time
            final SiteEntity finalSiteEntity = siteEntity;
            Thread statusUpdater = new Thread(() -> {
                while (indexingPools.containsKey(siteConfig.getUrl())) {
                    try {
                        Thread.sleep(5000); // Обновляем каждые 5 секунд
                        SiteEntity site = siteRepository.findById(finalSiteEntity.getId()).orElse(null);
                        if (site != null && site.getStatus() == IndexingStatus.INDEXING) {
                            site.setStatusTime(LocalDateTime.now());
                            siteRepository.save(site);
                            log.trace("Updated status_time for site: {}", site.getUrl());
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });
            statusUpdater.setDaemon(true);
            statusUpdater.start();

            // Запускаем индексацию
            pool.invoke(indexer);

            log.info("Completed crawling site: {}, starting lemmatization", siteEntity.getName());

            // Индексируем леммы
            indexLemmasForSite(siteEntity);

            // Обновляем статус на INDEXED
            siteEntity.setStatus(IndexingStatus.INDEXED);
            siteEntity.setStatusTime(LocalDateTime.now());
            siteRepository.save(siteEntity);
            siteRepository.flush();

            log.info("Successfully indexed site: {} ({} pages)",
                    siteEntity.getName(),
                    pageRepository.countBySite(siteEntity));

            indexingPools.remove(siteConfig.getUrl());
            siteIndexers.remove(siteConfig.getUrl());

        } catch (Exception e) {
            log.error("Error indexing site {}: {}", siteConfig.getUrl(), e.getMessage(), e);

            if (siteEntity != null) {
                final SiteEntity failedSite = siteEntity;
                siteRepository.findById(failedSite.getId()).ifPresent(site -> {
                    site.setStatus(IndexingStatus.FAILED);
                    site.setLastError(e.getMessage() != null ? e.getMessage() : "Unknown error");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                    siteRepository.flush();
                });
            } else {
                siteRepository.findByUrl(siteConfig.getUrl()).ifPresent(site -> {
                    site.setStatus(IndexingStatus.FAILED);
                    site.setLastError(e.getMessage() != null ? e.getMessage() : "Unknown error");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                    siteRepository.flush();
                });
            }
        } finally {
            if (indexingPools.isEmpty()) {
                isIndexing = false;
                log.info("All indexing tasks completed");
            }
        }
    }

    @Override
    public Response stopIndexing() {
        if (!isIndexing) {
            return new Response(false, "Индексация не запущена");
        }

        siteIndexers.values().forEach(SiteIndexer::stop);
        indexingPools.values().forEach(ForkJoinPool::shutdown);

        siteRepository.findAll().stream()
                .filter(site -> site.getStatus() == IndexingStatus.INDEXING)
                .forEach(site -> {
                    site.setStatus(IndexingStatus.FAILED);
                    site.setLastError("Индексация остановлена пользователем");
                    site.setStatusTime(LocalDateTime.now());
                    siteRepository.save(site);
                });

        isIndexing = false;
        indexingPools.clear();
        siteIndexers.clear();

        return new Response(true);
    }

    @Override
    @Transactional
    public Response indexPage(String url) {
        Site siteConfig = sitesList.getSites().stream()
                .filter(s -> url.startsWith(s.getUrl()))
                .findFirst().orElse(null);

        if (siteConfig == null) {
            return new Response(false,
                    "Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        try {
            SiteEntity siteEntity = siteRepository.findByUrl(siteConfig.getUrl())
                    .orElseGet(() -> {
                        SiteEntity newSite = new SiteEntity();
                        newSite.setUrl(siteConfig.getUrl());
                        newSite.setName(siteConfig.getName());
                        newSite.setStatus(IndexingStatus.INDEXED);
                        newSite.setStatusTime(LocalDateTime.now());
                        return siteRepository.save(newSite);
                    });

            String path = url.replace(siteConfig.getUrl(), "");
            if (path.isEmpty()) path = "/";

            pageRepository.findBySiteAndPath(siteEntity, path).ifPresent(page -> {
                indexRepository.deleteByPage(page);
                pageRepository.delete(page);
            });

            Connection.Response response = Jsoup.connect(url)
                    .userAgent(sitesList.getUserAgent())
                    .referrer(sitesList.getReferrer())
                    .ignoreHttpErrors(true)
                    .execute();

            Document doc = response.parse();

            PageEntity page = new PageEntity();
            page.setSite(siteEntity);
            page.setPath(path);
            page.setCode(response.statusCode());
            page.setContent(doc.outerHtml());
            page = pageRepository.save(page);

            if (response.statusCode() < 400) {
                indexPageLemmas(page);
            }

            return new Response(true);

        } catch (Exception e) {
            log.error("Error indexing page {}: {}", url, e.getMessage());
            return new Response(false, e.getMessage());
        }
    }

    private void indexLemmasForSite(SiteEntity site) {
        List<PageEntity> pages = pageRepository.findAll().stream()
                .filter(p -> p.getSite().getId().equals(site.getId()) && p.getCode() < 400)
                .toList();

        for (PageEntity page : pages) {
            try {
                indexPageLemmas(page);
            } catch (Exception e) {
                log.error("Error indexing lemmas for page {}: {}", page.getPath(), e.getMessage());
            }
        }
    }

    @Transactional
    protected void indexPageLemmas(PageEntity page) {
        String text = lemmaFinder.removeHtmlTags(page.getContent());
        Map<String, Integer> lemmas = lemmaFinder.collectLemmas(text);

        log.debug("Indexing {} lemmas for page: {}", lemmas.size(), page.getPath());

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String lemmaText = entry.getKey();
            Integer count = entry.getValue();

            try {
                LemmaEntity lemma = lemmaRepository
                        .findBySiteAndLemma(page.getSite(), lemmaText)
                        .orElseGet(() -> {
                            LemmaEntity newLemma = new LemmaEntity();
                            newLemma.setSite(page.getSite());
                            newLemma.setLemma(lemmaText);
                            newLemma.setFrequency(0);
                            return newLemma;
                        });

                boolean isNewPageForLemma = lemma.getId() == null ||
                        indexRepository.findByLemma(lemma).stream()
                                .noneMatch(idx -> idx.getPage().getId().equals(page.getId()));

                if (isNewPageForLemma) {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                }

                lemma = lemmaRepository.save(lemma);
                lemmaRepository.flush(); // Принудительная запись в БД

                IndexEntity index = new IndexEntity();
                index.setPage(page);
                index.setLemma(lemma);
                index.setRank((float) count);
                indexRepository.save(index);
                indexRepository.flush(); // Принудительная запись в БД

                log.trace("Saved lemma '{}' with frequency {} for page {}",
                        lemmaText, lemma.getFrequency(), page.getPath());

            } catch (Exception e) {
                log.error("Error saving lemma '{}' for page {}: {}",
                        lemmaText, page.getPath(), e.getMessage());
            }
        }

        log.debug("Completed indexing lemmas for page: {}", page.getPath());
    }
}
