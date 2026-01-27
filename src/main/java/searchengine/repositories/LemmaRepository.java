package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.LemmaEntity;
import searchengine.model.SiteEntity;

import java.util.List;
import java.util.Optional;

@Repository
public interface LemmaRepository extends JpaRepository<LemmaEntity, Long> {
    Optional<LemmaEntity> findBySiteAndLemma(SiteEntity site, String lemma);
    List<LemmaEntity> findBySiteAndLemmaIn(SiteEntity site, List<String> lemmas);
    long countBySite(SiteEntity site);
}
