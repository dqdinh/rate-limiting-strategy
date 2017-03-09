package com.lookout.ratelimitingfilter

/** A normalized request will match a rate limiting rule if the normalized request
  * is equal to the rule's id.
  */
object RateLimitRuleMatcher {
  def apply(
    rulesLookup: () => List[RateLimitRule],
    normalizedRequests: List[String]
  ): List[RateLimitRule] = {
    for {
      rule <- rulesLookup()
      normalizedRequest <- normalizedRequests
      if matchById(rule, normalizedRequest)
    } yield rule
  }

  def matchById(rule: RateLimitRule, normalizedRequest: String): Boolean = {
    rule match {
      case ServiceRuleId(id) => id == normalizedRequest
      case SubjectRuleId(id) => id == normalizedRequest
      case EnterpriseRuleId(id) => id == normalizedRequest
    }
  }
}
