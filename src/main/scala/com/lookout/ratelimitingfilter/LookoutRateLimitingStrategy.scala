package com.lookout.ratelimitingfilter

import java.util.UUID
import com.twitter.finagle.http.{Request}
import com.twitter.util.{Future}
import com.twitter.logging.Logger
import shapeless.tag._
import com.lookout.ratelimitingfilter.models._

/** Lookout strategy responsible for rate limiting requests.
  * This strategy is feed into the default Finagle RateLimitingFilter.
  *
  * Happy Paths
  * 1. The number of requests is below a rate limiting threshold.
  * 2. Redis store errors should turn off rate limiting.
  *
  * Sad Path
  * 1. The number of requests is over a rate limiting threshold.
  *
  * NOTE We use tagged types in the return types for `serviceLookup` and `claimLookup`
  * to distinguish common strings types from `ServiceName` string types and common UUIDs
  * from the two different types of Enterprise and Subject UUIDs.
  * For more information about tagged types see the following:
  *   http://underscore.io/blog/posts/2014/01/29/unboxed-tagged-angst.html
  *   http://eed3si9n.com/learning-scalaz/Tagged+type.html
  */
case class LookoutRateLimitingStrategy(
  serviceLookup: Request => Option[String @@ ServiceName],
  claimLookup: Request => Option[(UUID @@ EntClaim, UUID @@ SubClaim)],
  rulesLookup: () => List[RateLimitRule],
  processRule: RateLimitRule => Boolean
) {
  val LOG = Logger.get(getClass)

  def apply(request: Request): Future[Boolean] = {
    val normalizedRequests = RequestNormalization(serviceLookup, claimLookup, request)
    val matchedRules = RateLimitRuleMatcher(rulesLookup, normalizedRequests)
    val underThreshold = try {
      matchedRules.map(processRule).reduce(_ && _)
    } catch {
      case e: RedisError => {
        LOG.info("Redis error. Rate limiting is switched off.")
        true
      }
    }
    Future.value(underThreshold)
  }
}
