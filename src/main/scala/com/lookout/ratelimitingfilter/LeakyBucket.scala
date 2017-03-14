package com.lookout.ratelimitingfilter

import scala.util.{Success, Failure, Try}
import com.twitter.logging.Logger
import com.lookout.ratelimitingfilter.models._
import com.redis._

object LeakyBucket {
  val LOG = Logger.get(getClass)

  /** Checks if a batch of leak bucket redis operations are valid and parsable.
    * The last operation should always count the total number of tokens in a Bucket.
    * This operation should return a parsable integer string.
    *
    * NOTE the runRedisOps function returns an Option[List[Any]] because
    * the `scala-redis` library does not retain type signatures for Redis
    * pipeline transactions. This is why we have to use regex to parse out
    * the value of the last element in the list.
    */
  def leakAndIncBucketTokens(
    rule: RateLimitRule,
    runRedisOps: RateLimitRule => Option[List[Any]]
  ): Try[Int] = Try {
    val pattern = """Some\((\d+)\)""".r
    val result = runRedisOps(rule)
    result match {
      case Some(redisOperationResults) => {
        val pattern(tokenCount) = redisOperationResults.last.toString
        Integer.parseInt(tokenCount)
      }
      case None => throw RedisError(s"Redis batch operation failed for rule id: ${rule.id}")
    }
  }

  /** Checks if a rule has exceeded its threshold.
    * Passes on any errors.
    */
  def processRule(rule: RateLimitRule, leakAndIncFn: (RateLimitRule) => Try[Int]): Boolean = {
    leakAndIncFn(rule) match {
      case Success(tokenCount) if tokenCount < rule.threshold => true
      case Success(tokenCount) if tokenCount > rule.threshold => {
        LOG.info(s"Request is rate limited with rule id: ${rule.id} and threshold: ${rule.threshold}")
        false
      }
      case Failure(error) => throw error
    }
  }
}
