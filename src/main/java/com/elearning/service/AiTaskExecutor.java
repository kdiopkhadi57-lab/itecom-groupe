package com.elearning.service;

import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Threads des traitements par l'IA (lecture des copies, correction des cas pratiques).
 *
 * Exécuter ces traitements hors du thread de la requête évite le contexte JPA ouvert pour la vue,
 * qui garderait une connexion à la base pendant tout l'appel à l'IA. Le pool limite aussi le nombre
 * d'appels simultanés quand toute une classe rend sa copie en même temps.
 */
@Component
public class AiTaskExecutor {

    private final ExecutorService executor = Executors.newFixedThreadPool(8, runnable -> {
        Thread thread = new Thread(runnable, "devoir-ia");
        thread.setDaemon(true);
        return thread;
    });

    public <T> CompletableFuture<T> run(Supplier<T> task) {
        return CompletableFuture.supplyAsync(task, executor);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }
}
