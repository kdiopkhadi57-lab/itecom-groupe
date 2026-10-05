package com.elearning.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadDir.endsWith("/") ? uploadDir : uploadDir + "/";
        // Noms de fichiers uniques (UUID) : jamais modifiés, donc mis en cache par le navigateur.
        // Les requêtes partielles (Range) sont gérées : une vidéo démarre et avance sans être téléchargée en entier.
        registry.addResourceHandler("/uploads/**")
            .addResourceLocations("file:" + location)
            .setCacheControl(org.springframework.http.CacheControl.maxAge(java.time.Duration.ofDays(30)).cachePublic());
    }
}
