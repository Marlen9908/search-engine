package searchengine.services;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.config.SitesList;
import searchengine.model.PageEntity;
import searchengine.model.SiteEntity;
import searchengine.repositories.PageRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RecursiveAction;

@Slf4j
public class SiteIndexer extends RecursiveAction {

    private final String url;
    private final SiteEntity site;
    private final PageRepository pageRepository;
    private final Set<String> visitedUrls;
    private final SitesList config;
    private volatile boolean isStopped = false;

    public SiteIndexer(String url, SiteEntity site, PageRepository pageRepository,
                       Set<String> visitedUrls, SitesList config) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.visitedUrls = visitedUrls;
        this.config = config;
    }

    public void stop() {
        isStopped = true;
    }

    @Override
    protected void compute() {
        if (isStopped) {
            return;
        }

        String path = url.replace(site.getUrl(), "");
        if (path.isEmpty()) {
            path = "/";
        }

        // Проверяем, не посещали ли мы уже этот URL
        if (!visitedUrls.add(path)) {
            return;
        }

        try {
            // Задержка между запросами
            Thread.sleep(config.getDelayMs());

            // Получаем страницу
            Connection.Response response = Jsoup.connect(url)
                    .userAgent(config.getUserAgent())
                    .referrer(config.getReferrer())
                    .ignoreHttpErrors(true)
                    .timeout(10000) // 10 секунд таймаут
                    .execute();

            int statusCode = response.statusCode();
            Document doc = response.parse();

            // Сохраняем страницу
            PageEntity page = new PageEntity();
            page.setSite(site);
            page.setPath(path);
            page.setCode(statusCode);
            page.setContent(doc.outerHtml());

            synchronized (pageRepository) {
                try {
                    // Проверяем, не существует ли уже такая страница
                    pageRepository.findBySiteAndPath(site, path).ifPresent(existingPage -> {
                        page.setId(existingPage.getId()); // Обновляем существующую
                    });

                    pageRepository.save(page);
                    pageRepository.flush(); // Принудительная запись в БД

                    log.info("Indexed: {} (code: {}, size: {} bytes)",
                            url, statusCode, doc.outerHtml().length());
                } catch (Exception e) {
                    log.error("Error saving page {}: {}", url, e.getMessage());
                }
            }

            // Если код не успешный, не продолжаем обход
            if (statusCode >= 400) {
                log.warn("Skipping links from page {} due to error code {}", url, statusCode);
                return;
            }

            // Получаем ссылки на странице
            Elements links = doc.select("a[href]");
            List<SiteIndexer> tasks = new ArrayList<>();

            for (Element link : links) {
                if (isStopped) {
                    break;
                }

                String linkUrl = link.absUrl("href");

                // Проверяем, что ссылка принадлежит тому же сайту
                if (linkUrl.startsWith(site.getUrl()) &&
                        !linkUrl.contains("#") &&
                        !linkUrl.matches(".*\\.(jpg|jpeg|png|gif|pdf|zip|rar|exe|dmg)$")) {

                    String linkPath = linkUrl.replace(site.getUrl(), "");
                    if (linkPath.isEmpty()) {
                        linkPath = "/";
                    }

                    if (!visitedUrls.contains(linkPath)) {
                        SiteIndexer task = new SiteIndexer(linkUrl, site, pageRepository,
                                visitedUrls, config);
                        tasks.add(task);
                        task.fork();
                    }
                }
            }

            // Ждем завершения подзадач
            for (SiteIndexer task : tasks) {
                task.join();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Indexing interrupted for: {}", url);
        } catch (Exception e) {
            log.error("Error indexing {}: {}", url, e.getMessage());
        }
    }
}