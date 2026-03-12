package com.flightdeal.dao;

import com.flightdeal.generated.model.FlightDeal;

import java.util.List;

/**
 * Data Access Object interface for writing flight deal price records to the Price Store.
 * Implementations handle retry logic and persistence details.
 */
public interface PriceRecordDao {

    /**
     * Saves a single flight deal record to the Price Store.
     * The implementation uses the deal's destination as the partition key
     * and the retrieval timestamp as the sort key (ISO-8601).
     * Retry logic (up to 3 retries with exponential backoff) is handled by the implementation.
     *
     * @param deal               the flight deal to persist
     * @param retrievalTimestamp  the ISO-8601 timestamp of when the deal was retrieved
     */
    void save(FlightDeal deal, String retrievalTimestamp);

    /**
     * Saves a batch of flight deal records to the Price Store.
     * Each deal is written individually with retry logic applied per write.
     *
     * @param deals              the list of flight deals to persist
     * @param retrievalTimestamp  the ISO-8601 timestamp of when the deals were retrieved
     */
    void saveBatch(List<FlightDeal> deals, String retrievalTimestamp);
}
