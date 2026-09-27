package com.huixue.sandbox.infrastructure.pool;

import com.huixue.sandbox.config.DockerProperties;
import com.huixue.sandbox.infrastructure.docker.DockerContainerManager;
import com.huixue.sandbox.infrastructure.persistence.mapper.ContainerTaskMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ContainerPoolTest {

    private ContainerPool containerPool;
    private DockerContainerManager containerManager;
    private ContainerTaskMapper containerTaskMapper;

    @BeforeEach
    void setUp() {
        containerManager = Mockito.mock(DockerContainerManager.class);
        when(containerManager.createContainer(any())).thenReturn("mock-container-id-" + System.currentTimeMillis());

        containerTaskMapper = Mockito.mock(ContainerTaskMapper.class);

        DockerProperties properties = new DockerProperties();
        properties.getPool().setCoreSize(5);
        properties.getPool().setMaxSize(50);

        containerPool = new ContainerPool(containerManager, properties, containerTaskMapper);
        containerPool.init();
        
        // Sleep briefly to let async prewarm threads finish
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Test
    void testConcurrentBorrowAndReturn() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Try to borrow
                    PooledContainer container = containerPool.borrowContainer(com.huixue.sandbox.common.enums.LanguageType.JAVA, 5000);
                    if (container != null) {
                        successCount.incrementAndGet();
                        // Simulate work
                        Thread.sleep(50);
                        // Return
                        containerPool.returnContainer(container);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Check if all successfully borrowed (might take some time for pool to replenish up to 50 if core is 5, but wait time is 5000ms which should be enough)
        assertEquals(threadCount, successCount.get());
    }
}
