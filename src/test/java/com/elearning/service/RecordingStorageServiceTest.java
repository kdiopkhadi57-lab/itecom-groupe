package com.elearning.service;

import com.elearning.entity.VirtualClass;
import com.elearning.repository.VirtualClassRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RecordingStorageServiceTest {

    private final FileStorageService files = mock(FileStorageService.class);
    private final VideoOptimizationService optimizer = mock(VideoOptimizationService.class);
    private final RecordingStorageService service = new RecordingStorageService(files, optimizer,
        mock(VirtualClassRepository.class), mock(JdbcTemplate.class), mock(TransactionTemplate.class));

    @Test
    void playsTheFileWhenPresentAndFallsBackToDatabaseCopyWhenItWasErased() {
        VirtualClass vc = VirtualClass.builder().id(5L).recordingUrl("/uploads/recordings/a.webm").recordingData("AAAA").build();
        when(optimizer.bestUrl("/uploads/recordings/a.webm")).thenReturn("/uploads/recordings/a.opt.mp4");

        when(files.exists("/uploads/recordings/a.webm")).thenReturn(true);
        assertEquals("/uploads/recordings/a.opt.mp4", service.playbackUrl(vc));

        // Redéploiement sans volume : le fichier a disparu, la copie en base prend le relais
        when(files.exists("/uploads/recordings/a.webm")).thenReturn(false);
        assertEquals("/api/virtual-classes/5/recording", service.playbackUrl(vc));
        assertTrue(service.hasRecording(vc));

        vc.setRecordingData(null);
        assertNull(service.playbackUrl(new VirtualClass()));
    }
}
