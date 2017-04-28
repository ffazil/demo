package com.example;

import edu.stanford.nlp.pipeline.StanfordCoreNLP;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.util.Properties;

@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

	@Bean
	public StanfordCoreNLP stanfordCoreNLP() {
		Properties props = new Properties();
		props.setProperty("annotators", "tokenize, ssplit, parse, sentiment, pos, lemma");
		return new StanfordCoreNLP( props );
	}
}
