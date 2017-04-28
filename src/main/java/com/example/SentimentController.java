package com.example;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.pipeline.Annotation;
import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import edu.stanford.nlp.sentiment.SentimentCoreAnnotations;
import edu.stanford.nlp.util.CoreMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * @author firoz
 * @since 28/04/17
 */
@Slf4j
@Controller
public class SentimentController {

    @Autowired
    private StanfordCoreNLP pipeline;

    @RequestMapping(value = "/analyze", method = RequestMethod.POST)
    public ResponseEntity<?> analyze(@RequestBody Text text){
        Annotation document = pipeline.process(text.getContent());
        pipeline.annotate(document);
        List<CoreMap> sentences = document.get(CoreAnnotations.SentencesAnnotation.class);

        String s = null;
        if (sentences != null && ! sentences.isEmpty()) {
            CoreMap sentence = sentences.get(0);
            s = sentence.get(SentimentCoreAnnotations.SentimentClass.class);
            log.info("Sentiment = {}", s);
            }
        return ResponseEntity.ok(s);
    }
}
