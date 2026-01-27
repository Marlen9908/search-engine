package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import searchengine.dto.statistics.*;
import searchengine.model.IndexingStatus;
import searchengine.model.SiteEntity;
import searchengine.repositories.*;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    
    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    
    @Override
    public StatisticsResponse getStatistics() {
        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        
        StatisticsData data = new StatisticsData();
        
        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        
        List<SiteEntity> sites = siteRepository.findAll();
        
        total.setSites(sites.size());
        total.setPages(0);
        total.setLemmas(0);
        total.setIndexing(sites.stream().anyMatch(s -> s.getStatus() == IndexingStatus.INDEXING));
        
        for (SiteEntity site : sites) {
            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(site.getUrl());
            item.setName(site.getName());
            item.setStatus(site.getStatus().toString());
            item.setStatusTime(site.getStatusTime()
                    .atZone(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli());
            item.setError(site.getLastError() != null ? site.getLastError() : "");
            
            long pages = pageRepository.countBySite(site);
            long lemmas = lemmaRepository.countBySite(site);
            
            item.setPages(pages);
            item.setLemmas(lemmas);
            
            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);
            
            detailed.add(item);
        }
        
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        
        return response;
    }
}
