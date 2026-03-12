package com.flightdeal.dao;

import java.util.List;

/**
 * Data Access Object interface for writing flight deal price records to the Price Store.
 * Implementations handle retry logic and persistence details.
 */
public interface PriceRecordDao {

  /**
   * Saves a single price record entity to the Price Store. Retry logic (up to 3 retries with
   * exponential backoff) is handled by the implementation.
   *
   * @param entity the price record entity to persist
   */
  void save(PriceRecordEntity entity);

  /**
   * Saves a batch of price record entities to the Price Store. Each entity is written individually
   * with retry logic applied per write.
   *
   * @param entities the list of price record entities to persist
   */
  void saveBatch(List<PriceRecordEntity> entities);
}
