package com.lookout.ratelimitingfilter

import com.twitter.finagle.http.Request
import scala.util.{Success, Failure, Try}
import org.specs2.{Specification, ScalaCheck}
import org.scalacheck.Gen
import com.lookout.ratelimitingfilter.models._

class LeakyBucketSpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
  Leaky Bucket object
    when leaking and incrementing tokens
      And the result of Redis's batch of operations is `None`
        it should return a RedisError Failure                                 $redisError

      And the result of last operation from a batch of Redis calls
        is not a parsable string
          it should return a MatchError Failure                               $matchError

      And the result of the last operation from a batch of Redis calls
        is a parsable string
          it should return an integer representing the total number of tokens $tokenCount

    when processing a rule
      And there is a Redis error
        it should throw a RedisError                                          $throwRedisError

      And the token count exceeds the threshold
        it should throw a RequestRateLimitedError                             $throwRequestRateLimitedError

      And the token count is under the threshold
        it should be truthy                                                   $tokenCountUnderThreshold
  """

  def tokenCountUnderThreshold = prop {
    (rule: RateLimitRule) => {
      val tokenCount = rule.threshold - 1
      LeakyBucket.processRule(rule, (rule) => Success(tokenCount)) must_== true
    }
  }

  def throwRequestRateLimitedError = prop {
    (rule: RateLimitRule) => {
      val tokenCount = rule.threshold + 1
      val errorMessage = "Request rate limited"
      LeakyBucket.processRule(rule, (rule) => Success(tokenCount)) must_== false
    }
  }

  def throwRedisError = prop {
    (rule: RateLimitRule) => {
      val errorMessage = s"Redis batch operation failed for rule id: ${rule.id}"
      LeakyBucket.processRule(rule, (rule) => Failure(RedisError(errorMessage))) must throwA[RedisError](errorMessage)
    }
  }

  def redisError = prop {
    (rule: RateLimitRule) => {
      LeakyBucket.leakAndIncBucketTokens(rule, (rule) => None) must beFailedTry
        .withThrowable[RedisError](s"Redis batch operation failed for rule id: ${rule.id}")
    }
  }

  def matchError = prop {
    (
      rule: RateLimitRule,
      unParsableRedisBatchResult: List[Any]
    ) => {
      LeakyBucket.leakAndIncBucketTokens(rule, (rule) => Some(unParsableRedisBatchResult)) must beFailedTry
        .withThrowable[scala.MatchError]
    }
  }

  def tokenCount = prop {
    (
      rule: RateLimitRule,
      unParsableRedisBatchResult: List[Any]
    ) => {
      val tokenCount = Gen.posNum[Int].sample.get
      val validRedisBatchResult = unParsableRedisBatchResult ++ List(s"Some(${tokenCount.toString})")
      LeakyBucket.leakAndIncBucketTokens(rule, (rule) => Some(validRedisBatchResult)) must beSuccessfulTry.withValue(tokenCount)
    }
  }
}
