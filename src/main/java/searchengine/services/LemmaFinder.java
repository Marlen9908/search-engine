package searchengine.services;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class LemmaFinder {
    
    private final LuceneMorphology luceneMorphology;
    private static final Pattern WORD_PATTERN = Pattern.compile("[а-яёА-ЯЁ]+");
    private static final String[] PARTICLES = {"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ"};
    
    public LemmaFinder() throws IOException {
        this.luceneMorphology = new RussianLuceneMorphology();
    }
    
    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        String[] words = text.toLowerCase().split("\\s+");
        
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            
            List<String> lemmaList = getLemmas(word);
            for (String lemma : lemmaList) {
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }
        
        return lemmas;
    }
    
    public List<String> getLemmas(String word) {
        List<String> lemmas = new ArrayList<>();
        
        Matcher matcher = WORD_PATTERN.matcher(word);
        while (matcher.find()) {
            String foundWord = matcher.group().toLowerCase();
            
            if (foundWord.length() < 2) {
                continue;
            }
            
            try {
                List<String> morphInfo = luceneMorphology.getMorphInfo(foundWord);
                
                if (isServiceWord(morphInfo)) {
                    continue;
                }
                
                List<String> normalForms = luceneMorphology.getNormalForms(foundWord);
                lemmas.addAll(normalForms);
            } catch (Exception e) {
                // Слово не найдено в словаре, пропускаем
            }
        }
        
        return lemmas;
    }
    
    public Set<String> getLemmaSet(String text) {
        Set<String> lemmaSet = new HashSet<>();
        String[] words = text.toLowerCase().split("\\s+");
        
        for (String word : words) {
            lemmaSet.addAll(getLemmas(word));
        }
        
        return lemmaSet;
    }
    
    private boolean isServiceWord(List<String> morphInfo) {
        for (String info : morphInfo) {
            for (String particle : PARTICLES) {
                if (info.contains(particle)) {
                    return true;
                }
            }
        }
        return false;
    }
    
    public String removeHtmlTags(String html) {
        return html.replaceAll("<[^>]*>", " ")
                   .replaceAll("\\s+", " ")
                   .trim();
    }
}
