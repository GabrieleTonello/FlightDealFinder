package com.flightdeal.dao;

import com.flightdeal.generated.model.FlightDeal;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * DynamoDB implementation of {@link PriceRecordDao}.
 * Writes flight deal records with destination as partition key and timestamp as sort key.
 * Includes retry logic: up to 3 retries with exponential backoff on write failures.
 */
public class DynamoDbPriceRecordDao implements PriceRecordDao {

    private static final Logger LOG = Logger.getLogger(DynamoDbPriceRecordDao.class.getName());
    private static final int MAX_RETRIES = 3;
    private static final long BASE_DELAY_MS = 100;

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    @Inject
    public DynamoDbPriceRecordDao(DynamoDbClient dynamoDbClient,
                                   @Named("priceStoreTableName") String tableName) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tableName;
    }

    @Override
    public void save(FlightDeal deal, String retrievalTimestamp) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("destination", AttributeValue.fromS(deal.getDestination()));
        item.put("timestamp", AttributeValue.fromS(retrievalTimestamp));
        item.put("price", AttributeValue.fromN(deal.getPrice().toPlainString()));
        item.put("departureDate", AttributeValue.fromS(deal.getDepartureDate()));
        item.put("returnDate", AttributeValue.fromS(deal.getReturnDate()));
        item.put("airline", AttributeValue.fromS(deal.getAirline()));
        item.put("retrievalTimestamp", AttributeValue.fromS(retrievalTimestamp));

        PutItemRequest request = PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build();

        executeWithRetry(request, deal.getDestination());
    }

    @Override
    public void saveBatch(List<FlightDeal> deals, String retrievalTimestamp) {
        for (FlightDeal deal : deals) {
            save(deal, retrievalTimestamp);
        }
    }

    private void executeWithRetry(PutItemRequest request, String destination) {
        int attempt = 0;
        while (true) {
            try {
                dynamoDbClient.putItem(request);
                return;
            } catch (Exception e) {
                attempt++;
                if (attempt >= MAX_RETRIES) {
                    LOG.log(Level.SEVERE, "DynamoDB write for " + destination
                            + " failed after " + MAX_RETRIES + " attempts: " + e.getMessage(), e);
                    return;
                }
                long delay = BASE_DELAY_MS * (1L << (attempt - 1));
                LOG.log(Level.WARNING, "DynamoDB write for " + destination
                        + " attempt " + attempt + " failed, retrying in " + delay + "ms: " + e.getMessage());
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.log(Level.SEVERE, "DynamoDB write retry interrupted for " + destination, ie);
                    return;
                }
            }
        }
    }
}
