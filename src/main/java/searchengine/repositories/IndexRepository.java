package searchengine.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import searchengine.model.IndexEntity;
import searchengine.model.LemmaEntity;
import searchengine.model.PageEntity;

import java.util.List;

@Repository
public interface IndexRepository extends JpaRepository<IndexEntity, Long> {
    List<IndexEntity> findByLemma(LemmaEntity lemma);
    List<IndexEntity> findByPage(PageEntity page);
    void deleteByPage(PageEntity page);
}
