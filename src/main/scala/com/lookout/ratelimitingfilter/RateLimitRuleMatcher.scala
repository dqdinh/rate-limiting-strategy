package com.lookout.ratelimitingfilter

import com.lookout.ratelimitingfilter.models._

/** A normalized request will match a rate limiting rule if the normalized request
  * is equal to the rule's id.
  */
object RateLimitRuleMatcher {
  def apply(
    rulesLookup: () => List[RateLimitRule],
    normalizedRequests: List[String]
  ): List[RateLimitRule] = for {
    normalizedRequest <- normalizedRequests
    rule <- rulesLookup() if matchById(rule, normalizedRequest)
  } yield rule

  def matchById(rule: RateLimitRule, normalizedRequest: String): Boolean = {
    rule match {
      case ServiceRuleId(id) => id == normalizedRequest
      case SubjectRuleId(id) => id == normalizedRequest
      case EnterpriseRuleId(id) => id == normalizedRequest
    }
  }
}
