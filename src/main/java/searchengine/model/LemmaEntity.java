package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "lemma", indexes = {
    @Index(name = "idx_lemma", columnList = "lemma")
})
@Getter
@Setter
public class LemmaEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;
    
    @Column(columnDefinition = "VARCHAR(255)", nullable = false)
    private String lemma;
    
    @Column(nullable = false)
    private Integer frequency;
    
    @OneToMany(mappedBy = "lemma", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IndexEntity> indexes = new ArrayList<>();
}
