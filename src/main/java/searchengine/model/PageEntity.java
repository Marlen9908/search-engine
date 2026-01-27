package searchengine.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "page", indexes = {
    @Index(name = "idx_path", columnList = "path")
})
@Getter
@Setter
public class PageEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private SiteEntity site;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String path;
    
    @Column(nullable = false)
    private Integer code;
    
    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;
    
    @OneToMany(mappedBy = "page", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<IndexEntity> indexes = new ArrayList<>();
}
