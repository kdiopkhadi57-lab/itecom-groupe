package com.elearning.service;

import com.elearning.entity.Lesson;
import com.elearning.entity.Progress;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;

/**
 * Règles de progression d'une leçon et d'un cours, communes à l'étudiant et au suivi du professeur.
 * <ul>
 *   <li>Vidéo de la plateforme : terminée quand {@link #VIDEO_COMPLETION_PERCENT} % de la vidéo ont réellement été vus
 *       (plages lues sans saut : avancer la barre de lecture ne compte pas).</li>
 *   <li>Cours : moyenne des leçons, une leçon commencée comptant pour sa part (vidéo vue à moitié = 50 %).</li>
 * </ul>
 */
public final class LessonProgressService {

    public static final double VIDEO_COMPLETION_PERCENT = 90.0;
    /** Une vidéo de cours ne dure pas plus de 12 h : au-delà, la durée envoyée est rejetée. */
    static final double MAX_DURATION_SECONDS = 12 * 3600;

    private static final ObjectMapper JSON = new ObjectMapper();

    private LessonProgressService() {}

    /** Leçon vidéo dont la lecture peut être suivie (vidéo envoyée sur la plateforme). */
    public static boolean isTrackedVideo(Lesson lesson) {
        return lesson.getType() == Lesson.LessonType.VIDEO && lesson.getVideoUrl() != null
            && lesson.getVideoUrl().startsWith("/uploads/");
    }

    /** Plages [début, fin] en secondes, triées et fusionnées (chevauchements et contiguïtés). */
    public static List<double[]> merge(Collection<double[]> ranges, double duration) {
        List<double[]> clean = new ArrayList<>();
        for (double[] r : ranges) {
            if (r == null || r.length < 2 || Double.isNaN(r[0]) || Double.isNaN(r[1])) continue;
            double s = Math.max(0, Math.min(r[0], r[1])), e = Math.min(duration, Math.max(r[0], r[1]));
            if (e - s > 0.01) clean.add(new double[]{s, e});
        }
        clean.sort(Comparator.comparingDouble(r -> r[0]));
        List<double[]> merged = new ArrayList<>();
        for (double[] r : clean) {
            double[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
            if (last != null && r[0] <= last[1] + 0.5) last[1] = Math.max(last[1], r[1]);
            else merged.add(new double[]{r[0], r[1]});
        }
        return merged;
    }

    public static double total(List<double[]> ranges) {
        return ranges.stream().mapToDouble(r -> r[1] - r[0]).sum();
    }

    public static List<double[]> parse(String json) {
        if (json == null || json.isBlank()) return new ArrayList<>();
        try {
            return new ArrayList<>(JSON.readValue(json, new TypeReference<List<double[]>>() {}));
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    static String toJson(List<double[]> ranges) {
        try {
            // Arrondi au dixième de seconde : stockage compact
            List<double[]> rounded = ranges.stream()
                .map(r -> new double[]{Math.round(r[0] * 10) / 10.0, Math.round(r[1] * 10) / 10.0}).toList();
            return JSON.writeValueAsString(rounded);
        } catch (Exception e) {
            return "[]";
        }
    }

    /**
     * Ajoute les plages vues envoyées par le lecteur et met à jour position, pourcentage et achèvement.
     * Le pourcentage ne recule jamais ; une leçon terminée le reste.
     */
    public static void applyVideoWatch(Progress p, double duration, double position, Collection<double[]> newRanges) {
        if (!(duration > 0) || duration > MAX_DURATION_SECONDS) {
            throw new IllegalArgumentException("Durée de vidéo invalide.");
        }
        List<double[]> all = parse(p.getWatchedRanges());
        all.addAll(newRanges == null ? List.of() : newRanges);
        List<double[]> merged = merge(all, duration);
        p.setWatchedRanges(toJson(merged));
        p.setVideoDuration(duration);
        p.setVideoPosition(Math.max(0, Math.min(duration, position)));
        double watched = Math.min(100.0, total(merged) * 100.0 / duration);
        double current = p.getPercentage() == null ? 0 : p.getPercentage();
        if (!p.isCompleted()) {
            p.setPercentage(Math.max(current, Math.round(watched * 10) / 10.0));
            if (watched >= VIDEO_COMPLETION_PERCENT) {
                p.setCompleted(true);
                p.setPercentage(100.0);
            }
        }
    }

    /** Part d'une leçon dans la progression du cours (0 à 100). */
    public static double lessonPercent(Progress p) {
        if (p == null) return 0;
        if (p.isCompleted()) return 100;
        return Math.max(0, Math.min(100, p.getPercentage() == null ? 0 : p.getPercentage()));
    }

    /** Progression d'un cours : moyenne de ses leçons (une leçon non commencée compte 0). */
    public static double coursePercent(List<Lesson> lessons, Map<Long, Progress> byLesson) {
        if (lessons == null || lessons.isEmpty()) return 0;
        double sum = 0;
        for (Lesson l : lessons) sum += lessonPercent(byLesson.get(l.getId()));
        return Math.round(sum * 10 / lessons.size()) / 10.0;
    }

    public static long completedCount(List<Lesson> lessons, Map<Long, Progress> byLesson) {
        return lessons.stream().filter(l -> byLesson.containsKey(l.getId()) && byLesson.get(l.getId()).isCompleted()).count();
    }
}
