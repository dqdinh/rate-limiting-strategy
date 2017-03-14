package com.lookout.ratelimitingfilter

import com.redis._
import com.lookout.ratelimitingfilter.models._

object RedisOps {
  /** Implements the leaky bucket algorithm and executed as
    * a batch of atomic Redis operations that do the following:
    * (1) leak a bucket
    * (2) add a new set to a bucket
    * (3) reset the bucket's TTL
    * (4) return the number of elements in the bucket.
    */
  def processRateLimitRule(redis: RedisClient, rule: RateLimitRule): Option[List[Any]] = {
    /** FIXME `currentTime` should use the Redis server clock.
      * A pull request in scala-redis library to include `TIME` operation is required.
      * https://github.com/debasishg/scala-redis/pull/192
      */
    val currentTime = System.currentTimeMillis / 1000
    val bucketId = rule.id
    val period = rule.period
    val clearBefore = currentTime - period.inSeconds

    redis.pipeline {
      batch => {
        batch.zremrangebyscore(bucketId, 0, clearBefore)        // leak bucket
        batch.zadd(bucketId, currentTime, currentTime.toString) // add new set to bucket
        batch.expire(bucketId, period.inSeconds)                // reset bucket TTL
        batch.zcard(bucketId)                                   // return number of elements in bucket
      }
    }
  }
}
