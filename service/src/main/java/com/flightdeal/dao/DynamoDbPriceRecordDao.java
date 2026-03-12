package com.flightdeal.dao;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.util.List;

/**
 * DynamoDB Enhanced Client implementation of {@link PriceRecordDao}.
 * Uses {@link PriceRecordEntity} bean mapping for type-safe table operations.
 * Includes retry logic: up to 3 retries with exponential backoff on write failures.
 */
@Slf4j
@Singleton
public class DynamoDbPriceRecordDao implements PriceRecordDao {

    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 100;

    private final DynamoDbTable<PriceRecordEntity> table;

    @Inject
    public DynamoDbPriceRecordDao(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("FlightPriceHistory",
                TableSchema.fromBean(PriceRecordEntity.class));
    }

    @Override
    public void save(PriceRecordEntity entity) {
        executeWithRetry(entity);
    }

    @Override
    public void saveBatch(List<PriceRecordEntity> entities) {
        for (PriceRecordEntity entity : entities) {
            save(entity);
        }
    }

    private void executeWithRetry(PriceRecordEntity entity) {
        int attempt = 0;
        while (true) {
            try {
                table.putItem(entity);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    log.error("DynamoDB write for {} failed after {} attempts: {}",
                            entity.getDestination(), MAX_RETRIES, e.getMessage(), e);
                    return;
                }
                long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                log.warn("DynamoDB write for {} attempt {} failed, retrying in {}ms: {}",
                        entity.getDestination(), attempt, delay, e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.error("DynamoDB write retry interrupted for {}", entity.getDestination(), ie);
                    return;
                }
            }
        }
    }
}
