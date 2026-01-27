package searchengine.services;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Service;
import searchengine.dto.search.SearchData;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchServiceImpl implements SearchService {
    
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final IndexRepository indexRepository;
    private final LemmaFinder lemmaFinder;
    
    private static final int MAX_SNIPPET_LENGTH = 240;
    private static final double MAX_FREQUENCY_PERCENT = 0.8;
    
    @Override
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        
        if (query == null || query.isBlank()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }
        
        try {
            Set<String> queryLemmas = lemmaFinder.getLemmaSet(query);
            if (queryLemmas.isEmpty()) {
                response.setResult(true);
                response.setCount(0);
                response.setData(Collections.emptyList());
                return response;
            }
            
            List<SiteEntity> sites = getSitesForSearch(siteUrl);
            if (sites.isEmpty()) {
                response.setResult(false);
                response.setError("Указанный сайт не найден");
                return response;
            }
            
            Map<PageEntity, Float> relevanceMap = new HashMap<>();
            
            for (SiteEntity site : sites) {
                Map<PageEntity, Float> siteRelevance = searchInSite(site, queryLemmas);
                relevanceMap.putAll(siteRelevance);
            }
            
            List<SearchData> searchDataList = relevanceMap.entrySet().stream()
                    .sorted((e1, e2) -> Float.compare(e2.getValue(), e1.getValue()))
                    .skip(offset)
                    .limit(limit)
                    .map(entry -> createSearchData(entry.getKey(), entry.getValue(), queryLemmas))
                    .collect(Collectors.toList());
            
            response.setResult(true);
            response.setCount(relevanceMap.size());
            response.setData(searchDataList);
            
        } catch (Exception e) {
            log.error("Search error: {}", e.getMessage());
            response.setResult(false);
            response.setError("Ошибка поиска: " + e.getMessage());
        }
        
        return response;
    }
    
    private List<SiteEntity> getSitesForSearch(String siteUrl) {
        if (siteUrl == null || siteUrl.isBlank()) {
            return siteRepository.findAll();
        }
        return siteRepository.findByUrl(siteUrl)
                .map(List::of)
                .orElse(Collections.emptyList());
    }
    
    private Map<PageEntity, Float> searchInSite(SiteEntity site, Set<String> queryLemmas) {
        long totalPages = pageRepository.countBySite(site);
        if (totalPages == 0) {
            return Collections.emptyMap();
        }
        
        List<LemmaEntity> lemmas = lemmaRepository.findBySiteAndLemmaIn(site, new ArrayList<>(queryLemmas));
        
        lemmas = lemmas.stream()
                .filter(lemma -> lemma.getFrequency() < totalPages * MAX_FREQUENCY_PERCENT)
                .sorted(Comparator.comparingInt(LemmaEntity::getFrequency))
                .collect(Collectors.toList());
        
        if (lemmas.isEmpty()) {
            return Collections.emptyMap();
        }
        
        Set<PageEntity> pages = new HashSet<>(
            indexRepository.findByLemma(lemmas.get(0)).stream()
                .map(IndexEntity::getPage)
                .collect(Collectors.toSet())
        );
        
        for (int i = 1; i < lemmas.size(); i++) {
            Set<PageEntity> lemmaPages = indexRepository.findByLemma(lemmas.get(i)).stream()
                    .map(IndexEntity::getPage)
                    .collect(Collectors.toSet());
            pages.retainAll(lemmaPages);
            
            if (pages.isEmpty()) {
                return Collections.emptyMap();
            }
        }
        
        return calculateRelevance(pages, lemmas);
    }
    
    private Map<PageEntity, Float> calculateRelevance(Set<PageEntity> pages, List<LemmaEntity> lemmas) {
        Map<PageEntity, Float> relevanceMap = new HashMap<>();
        float maxAbsRelevance = 0;
        
        for (PageEntity page : pages) {
            float absRelevance = 0;
            
            for (LemmaEntity lemma : lemmas) {
                List<IndexEntity> indexes = indexRepository.findByLemma(lemma);
                for (IndexEntity index : indexes) {
                    if (index.getPage().getId().equals(page.getId())) {
                        absRelevance += index.getRank();
                        break;
                    }
                }
            }
            
            relevanceMap.put(page, absRelevance);
            if (absRelevance > maxAbsRelevance) {
                maxAbsRelevance = absRelevance;
            }
        }
        
        final float maxRel = maxAbsRelevance;
        if (maxRel > 0) {
            relevanceMap.replaceAll((page, rel) -> rel / maxRel);
        }
        
        return relevanceMap;
    }
    
    private SearchData createSearchData(PageEntity page, Float relevance, Set<String> queryLemmas) {
        String content = lemmaFinder.removeHtmlTags(page.getContent());
        String title = extractTitle(page.getContent());
        String snippet = generateSnippet(content, queryLemmas);
        
        return new SearchData(
                page.getSite().getUrl(),
                page.getSite().getName(),
                page.getPath(),
                title,
                snippet,
                relevance
        );
    }
    
    private String extractTitle(String html) {
        try {
            return Jsoup.parse(html).title();
        } catch (Exception e) {
            return "";
        }
    }
    
    private String generateSnippet(String text, Set<String> queryLemmas) {
        String[] sentences = text.split("[.!?]");
        StringBuilder snippet = new StringBuilder();
        
        for (String sentence : sentences) {
            Set<String> sentenceLemmas = lemmaFinder.getLemmaSet(sentence);
            
            boolean hasMatch = sentenceLemmas.stream()
                    .anyMatch(queryLemmas::contains);
            
            if (hasMatch) {
                String highlightedSentence = highlightWords(sentence, queryLemmas);
                snippet.append(highlightedSentence).append("... ");
                
                if (snippet.length() > MAX_SNIPPET_LENGTH) {
                    break;
                }
            }
        }
        
        String result = snippet.toString().trim();
        if (result.length() > MAX_SNIPPET_LENGTH) {
            result = result.substring(0, MAX_SNIPPET_LENGTH) + "...";
        }
        
        return result;
    }
    
    private String highlightWords(String sentence, Set<String> queryLemmas) {
        String[] words = sentence.split("\\s+");
        StringBuilder result = new StringBuilder();
        
        for (String word : words) {
            List<String> wordLemmas = lemmaFinder.getLemmas(word);
            
            boolean shouldHighlight = wordLemmas.stream()
                    .anyMatch(queryLemmas::contains);
            
            if (shouldHighlight) {
                result.append("<b>").append(word).append("</b> ");
            } else {
                result.append(word).append(" ");
            }
        }
        
        return result.toString().trim();
    }
}
