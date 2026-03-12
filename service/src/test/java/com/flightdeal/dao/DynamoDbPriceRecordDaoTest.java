package com.flightdeal.dao;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for DynamoDbPriceRecordDao.
 * Validates: Requirements 3.1, 3.3, 17.1, 17.5
 */
@ExtendWith(MockitoExtension.class)
class DynamoDbPriceRecordDaoTest {

    @Mock
    private DynamoDbEnhancedClient enhancedClient;

    @Mock
    private DynamoDbTable<PriceRecordEntity> table;

    private DynamoDbPriceRecordDao dao;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        when(enhancedClient.table(eq("FlightPriceHistory"), any(TableSchema.class)))
                .thenReturn(table);
        dao = new DynamoDbPriceRecordDao(enhancedClient);
    }

    private PriceRecordEntity sampleEntity(String destination) {
        return PriceRecordEntity.builder()
                .destination(destination)
                .timestamp("2025-07-01T12:00:00Z")
                .price(new BigDecimal("299.99"))
                .departureDate("2025-07-10")
                .returnDate("2025-07-17")
                .airline("TestAir")
                .retrievalTimestamp("2025-07-01T12:00:00Z")
                .build();
    }

    // ---- save calls putItem ----

    @Test
    void save_callsPutItem() {
        PriceRecordEntity entity = sampleEntity("Paris");

        dao.save(entity);

        verify(table).putItem(entity);
    }

    // ---- save retries on first failure then succeeds ----

    @Test
    void save_retriesOnFirstFailureThenSucceeds() {
        PriceRecordEntity entity = sampleEntity("Tokyo");

        doThrow(new RuntimeException("Transient error"))
                .doNothing()
                .when(table).putItem(entity);

        dao.save(entity);

        verify(table, times(2)).putItem(entity);
    }

    // ---- save fails after 3 retries and logs error ----

    @Test
    void save_failsAfterMaxRetriesAndLogsError() {
        PriceRecordEntity entity = sampleEntity("London");

        doThrow(new RuntimeException("Persistent error"))
                .when(table).putItem(entity);

        // Should not throw — logs error and returns after MAX_RETRIES
        dao.save(entity);

        verify(table, times(3)).putItem(entity);
    }

    // ---- saveBatch calls save for each entity ----

    @Test
    void saveBatch_callsSaveForEachEntity() {
        PriceRecordEntity entity1 = sampleEntity("Paris");
        PriceRecordEntity entity2 = sampleEntity("Tokyo");
        PriceRecordEntity entity3 = sampleEntity("London");

        dao.saveBatch(List.of(entity1, entity2, entity3));

        verify(table).putItem(entity1);
        verify(table).putItem(entity2);
        verify(table).putItem(entity3);
    }

    // ---- InterruptedException during retry sleep exits gracefully ----

    @Test
    void save_interruptedDuringRetrySleep_exitsGracefully() {
        PriceRecordEntity entity = sampleEntity("Berlin");

        doThrow(new RuntimeException("Transient error"))
                .when(table).putItem(entity);

        // Run save in a separate thread so we can interrupt it
        Thread testThread = new Thread(() -> dao.save(entity));
        testThread.start();

        // Give the thread time to enter the first retry sleep, then interrupt
        try {
            Thread.sleep(50);
            testThread.interrupt();
            testThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // The thread should have exited gracefully (not still running)
        assertFalse(testThread.isAlive(), "Thread should have exited after interruption");
    }
}
