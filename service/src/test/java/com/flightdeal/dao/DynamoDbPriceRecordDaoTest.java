package com.flightdeal.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** Unit tests for DynamoDbPriceRecordDao with the new PriceRecordEntity schema. */
@ExtendWith(MockitoExtension.class)
class DynamoDbPriceRecordDaoTest {

  @Mock private DynamoDbEnhancedClient enhancedClient;
  @Mock private DynamoDbTable<PriceRecordEntity> table;

  private DynamoDbPriceRecordDao dao;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    when(enhancedClient.table(eq("FlightPriceHistory"), any(TableSchema.class))).thenReturn(table);
    dao = new DynamoDbPriceRecordDao(enhancedClient);
  }

  private PriceRecordEntity sampleEntity(String route) {
    return PriceRecordEntity.builder()
        .route(route)
        .timestamp("2025-07-01T12:00:00Z")
        .price(299)
        .departureAirportId("JFK")
        .departureAirportName("John F. Kennedy")
        .departureTime("2025-07-01 10:00")
        .arrivalAirportId("CDG")
        .arrivalAirportName("Charles de Gaulle")
        .arrivalTime("2025-07-01 18:00")
        .airline("AirFrance")
        .totalDuration(480)
        .segments(1)
        .flightNumber("AF001")
        .dealType("best")
        .carbonEmissions(150000)
        .outboundDate("2025-07-01")
        .returnDate("2025-07-15")
        .build();
  }

  @Test
  void save_callsPutItem() {
    PriceRecordEntity entity = sampleEntity("JFK-CDG");
    dao.save(entity);
    verify(table).putItem(entity);
  }

  @Test
  void save_retriesOnFirstFailureThenSucceeds() {
    PriceRecordEntity entity = sampleEntity("LAX-NRT");
    doThrow(new RuntimeException("Transient error")).doNothing().when(table).putItem(entity);
    dao.save(entity);
    verify(table, times(2)).putItem(entity);
  }

  @Test
  void save_failsAfterMaxRetriesAndLogsError() {
    PriceRecordEntity entity = sampleEntity("LHR-FRA");
    doThrow(new RuntimeException("Persistent error")).when(table).putItem(entity);
    dao.save(entity);
    verify(table, times(3)).putItem(entity);
  }

  @Test
  void saveBatch_callsSaveForEachEntity() {
    PriceRecordEntity entity1 = sampleEntity("JFK-CDG");
    PriceRecordEntity entity2 = sampleEntity("LAX-NRT");
    PriceRecordEntity entity3 = sampleEntity("LHR-FRA");

    dao.saveBatch(List.of(entity1, entity2, entity3));

    verify(table).putItem(entity1);
    verify(table).putItem(entity2);
    verify(table).putItem(entity3);
  }

  @Test
  void save_interruptedDuringRetrySleep_exitsGracefully() {
    PriceRecordEntity entity = sampleEntity("JFK-CDG");
    doThrow(new RuntimeException("Transient error")).when(table).putItem(entity);

    Thread testThread = new Thread(() -> dao.save(entity));
    testThread.start();

    try {
      Thread.sleep(50);
      testThread.interrupt();
      testThread.join(2000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }

    assertFalse(testThread.isAlive(), "Thread should have exited after interruption");
  }
}
