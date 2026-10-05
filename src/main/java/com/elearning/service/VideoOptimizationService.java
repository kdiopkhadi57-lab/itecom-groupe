package com.elearning.service;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Vidéos plus légères et plus rapides à charger : chaque vidéo envoyée (leçon ou enregistrement de classe virtuelle)
 * est recompressée en arrière-plan avec ffmpeg en deux versions MP4 « démarrage rapide » (faststart) :
 * <ul>
 *   <li>« optimisée » : 720p maximum, pour la lecture normale ;</li>
 *   <li>« légère » : 360p, ~300 kbit/s, pour les connexions faibles et le téléchargement hors connexion.</li>
 * </ul>
 * L'original est conservé ; l'API renvoie la meilleure version disponible. Sans ffmpeg, rien ne change.
 */
@Service
@Slf4j
public class VideoOptimizationService {

    static final String OPTIMIZED_SUFFIX = ".opt.mp4";
    static final String LIGHT_SUFFIX = ".light.mp4";
    static final String FAILED_SUFFIX = ".optfailed";
    static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "m4v", "webm", "mkv", "avi", "3gp", "wmv", "mpeg", "mpg", "ogv");
    /** Dossiers d'uploads contenant des vidéos à optimiser. */
    static final List<String> VIDEO_DIRS = List.of("courses/videos", "recordings");

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.video.ffmpeg:ffmpeg}")
    private String ffmpeg = "ffmpeg";

    @Value("${app.video.optimize:true}")
    private boolean enabled = true;

    /** Une seule recompression à la fois : ffmpeg est gourmand en processeur. */
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "video-optimizer");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });
    private final Set<String> queued = ConcurrentHashMap.newKeySet();
    private volatile Boolean ffmpegAvailable;

    public record Variants(String optimizedUrl, String lightUrl, boolean processing) {}

    // ── Adresses des versions ──────────────────────────────────────────────────

    /** Version à lire : optimisée si elle existe, sinon l'adresse reçue. */
    public String bestUrl(String url) {
        String opt = variantUrl(url, OPTIMIZED_SUFFIX);
        return opt != null && exists(opt) ? opt : url;
    }

    /** Version légère si elle existe (sinon null). */
    public String lightUrl(String url) {
        String light = variantUrl(url, LIGHT_SUFFIX);
        return light != null && exists(light) ? light : null;
    }

    public boolean isProcessing(String url) {
        return url != null && queued.contains(url);
    }

    /**
     * « /uploads/courses/videos/abc.mov » → « /uploads/courses/videos/abc.opt.mp4 » (vidéos de /uploads seulement).
     * Fonctionne aussi à partir d'une version déjà recompressée (abc.opt.mp4 → abc.light.mp4).
     */
    static String variantUrl(String url, String suffix) {
        if (url == null || !url.startsWith("/uploads/")) return null;
        String lower = url.toLowerCase(Locale.ROOT);
        for (String s : List.of(OPTIMIZED_SUFFIX, LIGHT_SUFFIX)) {
            if (lower.endsWith(s)) return url.substring(0, url.length() - s.length()) + suffix;
        }
        if (!isVideo(url)) return null;
        return url.substring(0, url.lastIndexOf('.')) + suffix;
    }

    static boolean isVideo(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (name.endsWith(OPTIMIZED_SUFFIX) || name.endsWith(LIGHT_SUFFIX)) return false;
        int dot = name.lastIndexOf('.');
        return dot > 0 && VIDEO_EXTENSIONS.contains(name.substring(dot + 1));
    }

    Path pathOf(String url) {
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path p = root.resolve(url.substring("/uploads/".length())).normalize();
        if (!p.startsWith(root)) throw new IllegalArgumentException("Chemin invalide : " + url);
        return p;
    }

    private boolean exists(String url) {
        try {
            return Files.isRegularFile(pathOf(url));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ── Recompression ──────────────────────────────────────────────────────────

    /** Met la vidéo en file d'attente (sans effet si ffmpeg est absent ou si c'est déjà fait). */
    public void optimizeLater(String url) {
        // Seuls les originaux sont recompressés (pas les versions déjà produites)
        if (!enabled || url == null || !url.startsWith("/uploads/") || !isVideo(url)) return;
        if (exists(variantUrl(url, OPTIMIZED_SUFFIX)) && exists(variantUrl(url, LIGHT_SUFFIX))) return;
        if (!queued.add(url)) return;
        worker.submit(() -> {
            try {
                optimizeNow(url);
            } finally {
                queued.remove(url);
            }
        });
    }

    /** Recompression immédiate (appelée par la file d'attente). */
    void optimizeNow(String url) {
        if (!ffmpegAvailable()) return;
        Path source = pathOf(url);
        if (!Files.isRegularFile(source) || Files.exists(failedMarker(source))) return;
        try {
            long originalSize = Files.size(source);
            Path optimized = pathOf(variantUrl(url, OPTIMIZED_SUFFIX));
            if (!Files.exists(optimized)) {
                encode(source, optimized, optimizedArgs());
                // Une version « optimisée » plus lourde que l'original n'a pas d'intérêt, sauf pour un format non MP4
                if (Files.size(optimized) >= originalSize && url.toLowerCase(Locale.ROOT).endsWith(".mp4")) {
                    Files.copy(source, optimized, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Path light = pathOf(variantUrl(url, LIGHT_SUFFIX));
            if (!Files.exists(light)) encode(source, light, lightArgs());
            log.info("Vidéo optimisée {} : {} Ko → {} Ko (légère : {} Ko)", url, originalSize / 1024,
                Files.size(optimized) / 1024, Files.size(light) / 1024);
        } catch (Exception e) {
            log.warn("Optimisation impossible pour {} : {}", url, e.getMessage());
            try { Files.writeString(failedMarker(source), String.valueOf(e.getMessage())); } catch (IOException ignored) { }
        }
    }

    /** 720p maximum, H.264 qualité moyenne, son mono 64 kbit/s, lecture dès les premiers octets. */
    static List<String> optimizedArgs() {
        return List.of("-vf", "scale=-2:'trunc(min(720,ih)/2)*2'", "-c:v", "libx264", "-preset", "veryfast", "-crf", "28",
            "-maxrate", "1200k", "-bufsize", "2400k", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "64k", "-ac", "1", "-movflags", "+faststart");
    }

    /** 360p, 15 images/s, ~300 kbit/s : pour les connexions faibles et le hors connexion. */
    static List<String> lightArgs() {
        return List.of("-vf", "scale=-2:'trunc(min(360,ih)/2)*2',fps=15", "-c:v", "libx264", "-preset", "veryfast", "-crf", "30",
            "-maxrate", "300k", "-bufsize", "600k", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "48k", "-ac", "1", "-movflags", "+faststart");
    }

    /** Écrit dans un fichier temporaire puis le renomme : jamais de vidéo à moitié écrite servie aux étudiants. */
    private void encode(Path source, Path target, List<String> args) throws IOException, InterruptedException {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp.mp4");
        List<String> cmd = new ArrayList<>(List.of(ffmpeg, "-y", "-hide_banner", "-loglevel", "error", "-threads", "2",
            "-i", source.toString()));
        cmd.addAll(args);
        cmd.add(tmp.toString());
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
        if (!p.waitFor(3, TimeUnit.HOURS)) {
            p.destroyForcibly();
            Files.deleteIfExists(tmp);
            throw new IOException("ffmpeg trop long");
        }
        if (p.exitValue() != 0 || !Files.exists(tmp) || Files.size(tmp) == 0) {
            Files.deleteIfExists(tmp);
            throw new IOException("ffmpeg a échoué (code " + p.exitValue() + ")");
        }
        Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static Path failedMarker(Path source) {
        return source.resolveSibling(source.getFileName() + FAILED_SUFFIX);
    }

    boolean ffmpegAvailable() {
        if (ffmpegAvailable == null) {
            try {
                Process p = new ProcessBuilder(ffmpeg, "-version").redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD).start();
                ffmpegAvailable = p.waitFor(10, TimeUnit.SECONDS) && p.exitValue() == 0;
            } catch (Exception e) {
                ffmpegAvailable = false;
            }
            if (!ffmpegAvailable) log.warn("ffmpeg introuvable : les vidéos sont servies sans recompression.");
        }
        return ffmpegAvailable;
    }

    /** Au démarrage : optimise les vidéos déjà envoyées qui ne l'ont pas encore été. */
    @EventListener(ApplicationReadyEvent.class)
    public void optimizeExistingVideos() {
        if (!enabled || !ffmpegAvailable()) return;
        Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
        for (String dir : VIDEO_DIRS) {
            Path d = root.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            try (Stream<Path> files = Files.list(d)) {
                files.filter(Files::isRegularFile)
                    .map(f -> "/uploads/" + dir + "/" + f.getFileName())
                    .filter(VideoOptimizationService::isVideo)
                    .forEach(this::optimizeLater);
            } catch (IOException e) {
                log.warn("Parcours des vidéos impossible dans {} : {}", d, e.getMessage());
            }
        }
    }

    /** Supprime les versions recompressées d'une vidéo (quand l'original est supprimé). */
    public void deleteVariants(String url) {
        for (String suffix : List.of(OPTIMIZED_SUFFIX, LIGHT_SUFFIX)) {
            String v = variantUrl(url, suffix);
            if (v != null) {
                try { Files.deleteIfExists(pathOf(v)); } catch (Exception ignored) { }
            }
        }
    }

    @PreDestroy
    void shutdown() {
        worker.shutdownNow();
    }
}
