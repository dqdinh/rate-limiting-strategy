package com.lookout.ratelimitingfilter

import com.redis._
import com.lookout.ratelimitingfilter.models._

object RedisOps {
  /** Runs the leaky bucket algorithm in a fail fast manner
    * using Option. If any function returns `None`, then the for comprehension
    * will short circuit and exit. This means we're treat `None` as an error case.
    * While is not optimal, it did align with scala-redis's pipeline function
    * which returns `None` as a error case.
    * https://github.com/debasishg/scala-redis/blob/master/src/main/scala/com/redis/RedisClient.scala#L126-L128
    *
    * TODO Use Either instead of Option to better describe Redis error cases.
    * TODO Create pull request to replace `None` with an actual error in scala-redis
    *
    */
  def runLeakyBucket(redis: RedisClient, rule: RateLimitRule): Option[List[Any]] = for {
    timestamp <- getCurrentTime(redis)
    timestamp <- Option(Integer.parseInt(timestamp))
    result <- processRateLimitRule(redis, rule, timestamp)
  } yield result

  /** Fetches the current (seconds-denoted) timestamp from a master Redis server.
    * Using a single source of time prevents any clock skew that might occur
    * in a distributed system.
    */
  def getCurrentTime(redis: RedisClient): Option[String] = redis.time match {
    case Some(Some(timestamp) :: xs)=> Some(timestamp.toString)
    case _ => None
  }

  /** Implements the leaky bucket algorithm and executed as
    * a batch of atomic Redis operations.
    */
  def processRateLimitRule(redis: RedisClient, rule: RateLimitRule, currentTime: Int): Option[List[Any]] = {
    val bucketId = rule.id
    val period = rule.period.inSeconds

    try {
      redis.pipeline {
        batch => {
          batch.zremrangebyscore(bucketId, 0, currentTime - period) // leak bucket
          batch.zadd(bucketId, currentTime, currentTime.toString)   // add new set to bucket
          batch.expire(bucketId, period)                            // reset bucket TTL
          batch.zcard(bucketId)                                     // return number of elements in bucket
        }
      }
    } catch {
      case e: Exception =>
        throw RedisError(s"Error from one of the batched Redis commands: ${e.getMessage}")
    }
  }
}
