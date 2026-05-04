package com.pfe.callmgmt.decision.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Configuration du RestTemplate pour les appels LLM (Gemma 4 via Ollama).
 * SonarCloud fix B2 — RestTemplate injecté au lieu d'être créé à chaque appel
 * pour éviter les resource leaks (connexions/threads non libérés).
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate llmRestTemplate(
            @Value("${openai.timeout-seconds:30}") int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutSeconds * 1000);
        factory.setReadTimeout(timeoutSeconds * 1000);
        return new RestTemplate(factory);
    }
}
