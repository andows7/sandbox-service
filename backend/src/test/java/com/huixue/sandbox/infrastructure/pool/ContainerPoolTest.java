package com.huixue.sandbox.infrastructure.pool;

import com.huixue.sandbox.common.enums.LanguageType;
import com.huixue.sandbox.config.DockerProperties;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.persistence.mapper.ContainerTaskMapper;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class ContainerPoolTest {
    @TempDir Path work;
    ContainerPool pool;
    DockerContainerManager manager;
    DockerProperties properties;
    AtomicInteger sequence = new AtomicInteger();

    @BeforeEach void setUp() {
        manager = mock(DockerContainerManager.class);
        when(manager.createContainer(any())).thenAnswer(i -> "container-" + sequence.incrementAndGet());
        properties = new DockerProperties();
        properties.setWorkDir(work.toString());
        pool = new ContainerPool(manager, properties, mock(ContainerTaskMapper.class));
        pool.init();
    }
    @AfterEach void tearDown() { pool.close(); }

    @Test void zeroWarmupAndOnDemandCreation() throws Exception {
        verifyNoInteractions(manager);
        var container = pool.borrowContainer(LanguageType.C, 100);
        assertNotNull(container);
        assertEquals(1, pool.getTotalContainers());
        pool.returnContainer(container);
        assertEquals(0, pool.getTotalContainers());
        pool.returnContainer(container);
        assertEquals(0, pool.getTotalContainers(), "Duplicate returns must not free another slot");
        try (var files = java.nio.file.Files.list(work)) { assertEquals(0, files.count()); }
    }

    @Test void languageCapAndGlobalCap() throws Exception {
        var c1 = pool.borrowContainer(LanguageType.C, 100);
        var c2 = pool.borrowContainer(LanguageType.C, 100);
        assertNull(pool.borrowContainer(LanguageType.C, 20));
        var j = pool.borrowContainer(LanguageType.JAVA, 100);
        var p = pool.borrowContainer(LanguageType.PYTHON, 100);
        assertEquals(4, pool.getTotalContainers());
        assertNull(pool.borrowContainer(LanguageType.CPP, 20));
        pool.returnContainer(c1);
        assertNotNull(pool.borrowContainer(LanguageType.CPP, 100));
    }

    @Test void concurrentReservationsNeverExceedGlobalLimit() throws Exception {
        var executor = Executors.newFixedThreadPool(12);
        try {
            var futures = new ArrayList<Future<?>>();
            AtomicInteger peak = new AtomicInteger();
            for (int i=0; i<48; i++) {
                var lang = LanguageType.values()[i%4];
                futures.add(executor.submit(() -> {
                    try {
                        var c = pool.borrowContainer(lang, 3000);
                        assertNotNull(c);
                        peak.accumulateAndGet(pool.getTotalContainers(), Math::max);
                        Thread.sleep(5);
                        pool.returnContainer(c);
                    } catch (InterruptedException e) { throw new RuntimeException(e); }
                }));
            }
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
            assertTrue(peak.get() <= 4);
            assertEquals(0, pool.getTotalContainers());
        } finally { executor.shutdownNow(); }
    }

    @Test void failedCreationReleasesCapacity() throws Exception {
        when(manager.createContainer(any())).thenThrow(new IllegalStateException("unavailable"));
        assertNull(pool.borrowContainer(LanguageType.C, 100));
        assertEquals(0, pool.getTotalContainers());
        try (var files = java.nio.file.Files.list(work)) { assertEquals(0, files.count()); }
    }

    @Test void shutdownRemovesBorrowedContainersAndRejectsNewWork() throws Exception {
        var c = pool.borrowContainer(LanguageType.C, 100);
        pool.close();
        verify(manager).stopAndRemoveContainer(c.getContainerId());
        assertEquals(0, pool.getTotalContainers());
        assertNull(pool.borrowContainer(LanguageType.C, 100));
    }

    @Test void failedRemovalKeepsReservationUntilRetry() throws Exception {
        var c = pool.borrowContainer(LanguageType.C, 100);
        doThrow(new IllegalStateException("Docker temporarily unavailable"))
                .doNothing().when(manager).stopAndRemoveContainer(c.getContainerId());
        pool.returnContainer(c);
        assertEquals(1, pool.getTotalContainers());
        assertTrue(pool.isManaged(c.getContainerId()));
        pool.retryFailedCleanup();
        assertEquals(0, pool.getTotalContainers());
        assertFalse(pool.isManaged(c.getContainerId()));
    }

    @Test void failedStartWithFailedRemovalAlsoRetainsReservation() throws Exception {
        doThrow(new IllegalStateException("start unavailable")).when(manager).startContainer(anyString());
        doThrow(new IllegalStateException("remove unavailable")).doNothing()
                .when(manager).stopAndRemoveContainer("container-1");
        assertNull(pool.borrowContainer(LanguageType.C, 100));
        assertEquals(1, pool.getTotalContainers());
        pool.retryFailedCleanup();
        assertEquals(0, pool.getTotalContainers());
    }
}
