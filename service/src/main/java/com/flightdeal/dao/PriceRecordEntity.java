package com.flightdeal.dao;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

/**
 * DynamoDB bean entity for the FlightPriceHistory table. Maps directly to the table schema with
 * destination as partition key and timestamp as sort key.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class PriceRecordEntity {

  private String destination;
  private String timestamp;
  private BigDecimal price;
  private String departureDate;
  private String returnDate;
  private String airline;
  private String retrievalTimestamp;

  @DynamoDbPartitionKey
  public String getDestination() {
    return destination;
  }

  @DynamoDbSortKey
  public String getTimestamp() {
    return timestamp;
  }
}
