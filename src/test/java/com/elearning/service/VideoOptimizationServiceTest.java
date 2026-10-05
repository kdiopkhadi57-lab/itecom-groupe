package com.elearning.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VideoOptimizationServiceTest {

    @TempDir Path uploads;
    private VideoOptimizationService service;

    @BeforeEach
    void setUp() {
        service = new VideoOptimizationService();
        ReflectionTestUtils.setField(service, "uploadDir", uploads.toString());
    }

    @Test
    void variantsAreDerivedFromOriginalOrFromAnAlreadyOptimizedUrl() {
        assertEquals("/uploads/courses/videos/abc.opt.mp4", VideoOptimizationService.variantUrl("/uploads/courses/videos/abc.MOV", ".opt.mp4"));
        assertEquals("/uploads/courses/videos/abc.light.mp4", VideoOptimizationService.variantUrl("/uploads/courses/videos/abc.opt.mp4", ".light.mp4"));
        assertNull(VideoOptimizationService.variantUrl("/uploads/courses/documents/cours.pdf", ".opt.mp4"));
        assertNull(VideoOptimizationService.variantUrl("https://youtube.com/watch?v=x", ".opt.mp4"));
        assertFalse(VideoOptimizationService.isVideo("/uploads/courses/videos/abc.opt.mp4"));   // jamais recompressée deux fois
    }

    @Test
    void servesOriginalUntilOptimizedVersionExists() throws Exception {
        Path dir = Files.createDirectories(uploads.resolve("courses/videos"));
        Files.write(dir.resolve("abc.mov"), new byte[]{1});
        assertEquals("/uploads/courses/videos/abc.mov", service.bestUrl("/uploads/courses/videos/abc.mov"));
        assertNull(service.lightUrl("/uploads/courses/videos/abc.mov"));

        Files.write(dir.resolve("abc.opt.mp4"), new byte[]{1});
        Files.write(dir.resolve("abc.light.mp4"), new byte[]{1});
        assertEquals("/uploads/courses/videos/abc.opt.mp4", service.bestUrl("/uploads/courses/videos/abc.mov"));
        assertEquals("/uploads/courses/videos/abc.light.mp4", service.lightUrl("/uploads/courses/videos/abc.mov"));
        // Un professeur qui renvoie l'adresse optimisée garde ses deux versions
        assertEquals("/uploads/courses/videos/abc.light.mp4", service.lightUrl("/uploads/courses/videos/abc.opt.mp4"));
    }

    @Test
    void withoutFfmpegNothingIsProducedAndNothingFails() throws Exception {
        ReflectionTestUtils.setField(service, "ffmpeg", "/chemin/inexistant/ffmpeg");
        Path dir = Files.createDirectories(uploads.resolve("courses/videos"));
        Files.write(dir.resolve("abc.mp4"), new byte[]{1, 2, 3});
        service.optimizeNow("/uploads/courses/videos/abc.mp4");
        assertFalse(Files.exists(dir.resolve("abc.opt.mp4")));
        assertEquals("/uploads/courses/videos/abc.mp4", service.bestUrl("/uploads/courses/videos/abc.mp4"));
    }

    /**
     * Vraie recompression : lancée seulement si ffmpeg est fourni (-Dffmpeg.path=/chemin/ffmpeg),
     * l'image Docker de production l'installe.
     */
    @Test
    void realEncodingProducesSmallerFaststartMp4s() throws Exception {
        String ffmpeg = System.getProperty("ffmpeg.path");
        Assumptions.assumeTrue(ffmpeg != null && Files.isExecutable(Path.of(ffmpeg)), "ffmpeg non fourni");
        ReflectionTestUtils.setField(service, "ffmpeg", ffmpeg);
        Path dir = Files.createDirectories(uploads.resolve("courses/videos"));
        Path source = dir.resolve("cours.mov");
        // Vidéo de 20 s en 1080p (hauteur impaire comprise dans le test), débit élevé, son
        Process p = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=size=1920x1081:rate=30:duration=20",
            "-f", "lavfi", "-i", "sine=frequency=440:duration=20",
            "-c:v", "libx264", "-b:v", "6M", "-c:a", "aac", "-shortest", source.toString()).inheritIO().start();
        assertEquals(0, p.waitFor());

        service.optimizeNow("/uploads/courses/videos/cours.mov");

        Path opt = dir.resolve("cours.opt.mp4"), light = dir.resolve("cours.light.mp4");
        assertTrue(Files.exists(opt) && Files.exists(light), "versions produites");
        long src = Files.size(source);
        assertTrue(Files.size(opt) < src / 2, "optimisée nettement plus légère : " + Files.size(opt) + " / " + src);
        assertTrue(Files.size(light) < Files.size(opt), "légère plus petite que l'optimisée");
        for (Path f : List.of(opt, light)) {
            byte[] head = Files.readAllBytes(f);
            String start = new String(head, 0, Math.min(head.length, 4096), java.nio.charset.StandardCharsets.ISO_8859_1);
            int moov = start.indexOf("moov"), mdat = start.indexOf("mdat");
            assertTrue(moov >= 0 && (mdat < 0 || moov < mdat), "index (moov) en tête de fichier : lecture immédiate");
        }
        System.out.printf("original %d Ko → optimisée %d Ko, légère %d Ko%n", src / 1024, Files.size(opt) / 1024, Files.size(light) / 1024);

        // Enregistrement de classe virtuelle (WebM VP8 + Opus, comme le navigateur)
        Path recDir = Files.createDirectories(uploads.resolve("recordings"));
        Path webm = recDir.resolve("seance.webm");
        Process r = new ProcessBuilder(ffmpeg, "-y", "-loglevel", "error",
            "-f", "lavfi", "-i", "testsrc2=size=960x540:rate=12:duration=10",
            "-f", "lavfi", "-i", "sine=frequency=300:duration=10",
            "-c:v", "libvpx", "-b:v", "300k", "-c:a", "libopus", "-shortest", webm.toString()).inheritIO().start();
        assertEquals(0, r.waitFor());
        service.optimizeNow("/uploads/recordings/seance.webm");
        assertTrue(Files.exists(recDir.resolve("seance.opt.mp4")) && Files.exists(recDir.resolve("seance.light.mp4")),
            "un enregistrement WebM obtient aussi ses versions MP4");
    }
}
