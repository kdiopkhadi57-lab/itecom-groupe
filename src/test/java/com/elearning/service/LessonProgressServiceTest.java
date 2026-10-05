package com.elearning.service;

import com.elearning.entity.Lesson;
import com.elearning.entity.Progress;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LessonProgressServiceTest {

    private static Progress fresh() { return Progress.builder().percentage(0.0).build(); }

    @Test
    void onlyReallyWatchedRangesCountAndSeekingDoesNot() {
        Progress p = fresh();
        // 100 s de vidéo : 0→30 regardé, saut à 95, 95→100 regardé
        LessonProgressService.applyVideoWatch(p, 100, 100, List.of(new double[]{0, 30}, new double[]{95, 100}));
        assertEquals(35.0, p.getPercentage());
        assertFalse(p.isCompleted(), "sauter à la fin ne termine pas la leçon");
        assertEquals(100.0, p.getVideoPosition());
    }

    @Test
    void repeatedOrReplayedRangesAreNotCountedTwiceAndProgressNeverGoesBack() {
        Progress p = fresh();
        LessonProgressService.applyVideoWatch(p, 200, 60, List.of(new double[]{0, 60}));
        LessonProgressService.applyVideoWatch(p, 200, 60, List.of(new double[]{0, 60}));          // envoi rejoué (hors connexion)
        LessonProgressService.applyVideoWatch(p, 200, 80, List.of(new double[]{50, 80}));         // chevauchement
        assertEquals(40.0, p.getPercentage());
        LessonProgressService.applyVideoWatch(p, 200, 10, List.of());                             // retour en arrière
        assertEquals(40.0, p.getPercentage(), "la progression ne recule pas");
        assertEquals(10.0, p.getVideoPosition(), "la position de reprise suit le lecteur");
        assertEquals(1, LessonProgressService.parse(p.getWatchedRanges()).size(), "plages fusionnées");
    }

    @Test
    void ninetyPercentWatchedCompletesTheLesson() {
        Progress p = fresh();
        LessonProgressService.applyVideoWatch(p, 300, 270, List.of(new double[]{0, 269}));
        assertFalse(p.isCompleted());
        LessonProgressService.applyVideoWatch(p, 300, 271, List.of(new double[]{269, 271}));
        assertTrue(p.isCompleted());
        assertEquals(100.0, p.getPercentage());
    }

    @Test
    void invalidDurationOrRangesAreRejectedOrClamped() {
        assertThrows(IllegalArgumentException.class, () -> LessonProgressService.applyVideoWatch(fresh(), 0, 0, List.of()));
        assertThrows(IllegalArgumentException.class, () -> LessonProgressService.applyVideoWatch(fresh(), 1e9, 0, List.of()));
        Progress p = fresh();
        LessonProgressService.applyVideoWatch(p, 100, 0, List.of(new double[]{-50, 20}, new double[]{90, 500}, new double[]{Double.NaN, 3}));
        assertEquals(30.0, p.getPercentage(), "plages ramenées dans [0, durée]");
    }

    @Test
    void coursePercentCountsStartedLessonsForTheirShare() {
        Lesson a = Lesson.builder().id(1L).build(), b = Lesson.builder().id(2L).build(), c = Lesson.builder().id(3L).build();
        Progress done = Progress.builder().completed(true).percentage(100.0).build();
        Progress half = Progress.builder().percentage(50.0).build();
        assertEquals(50.0, LessonProgressService.coursePercent(List.of(a, b, c), Map.of(1L, done, 2L, half)));
        assertEquals(1, LessonProgressService.completedCount(List.of(a, b, c), Map.of(1L, done, 2L, half)));
        assertEquals(0.0, LessonProgressService.coursePercent(List.of(), Map.of()));
    }

    @Test
    void onlyPlatformVideosAreTracked() {
        assertTrue(LessonProgressService.isTrackedVideo(Lesson.builder().type(Lesson.LessonType.VIDEO).videoUrl("/uploads/courses/videos/a.mp4").build()));
        assertFalse(LessonProgressService.isTrackedVideo(Lesson.builder().type(Lesson.LessonType.VIDEO).videoUrl("https://youtube.com/x").build()));
        assertFalse(LessonProgressService.isTrackedVideo(Lesson.builder().type(Lesson.LessonType.PDF).videoUrl("/uploads/x.mp4").build()));
    }
}
