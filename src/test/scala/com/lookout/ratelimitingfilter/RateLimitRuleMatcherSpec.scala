package com.lookout.ratelimitingfilter

import java.util.UUID
import java.net.URLEncoder
import com.twitter.finagle.http.{Method, Request}
import org.specs2.{Specification, ScalaCheck}
import shapeless.tag._
import com.lookout.ratelimitingfilter.models._

class RateLimitRuleMatcherSpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
  The RateLimitRuleMatcher object
    when a list of normalized HTTP requests matches all rules
      it returns a list of all the rules                                   $allRulesMatch

    when a list of normalized HTTP requests matches no rules
      it returns an empty list                                             $noRulesMatch

    when one normalized HTTP request matches one rule
      it returns a list containing the matching rule                       $oneRuleMatch

  The matchById method
    when it receives a normalized HTTP request
      it matches a rate limiting rule with a service name target           $serviceRuleMatch
      it does not match a rate limiting rule with a service name target    $serviceRuleNoMatch

      it matches a rate limiting rule with a subject UUID target           $subjectRuleMatch
      it does not match a rate limiting rule with a subject UUID target    $subjectRuleNoMatch

      it matches a rate limiting rule with a enterprise UUID target        $enterpriseRuleMatch
      it does not match a rate limiting rule with a enterprise UUID target $enterpriseRuleNoMatch
  """

  def allRulesMatch = prop {
    (
      rulesTupleList: List[(RateLimitRule, Request)]
    ) => {
      val (rules, requests) = rulesTupleList.unzip
      val normalizedRequests = rules.map(_.id)
      RateLimitRuleMatcher(() => rules, normalizedRequests) must_== rules
    }
  }

  def noRulesMatch = prop {
    (
      normalizedRequests: List[String],
      rulesTupleList: List[(RateLimitRule, Request)]
    ) => {
      val (rules, requests) = rulesTupleList.unzip
      RateLimitRuleMatcher(() => rules, normalizedRequests) must_== Nil
    }
  }

  def oneRuleMatch = prop {
    (
      rulesTupleList: List[(RateLimitRule, Request)]
    ) => {
      val (rules, requests) = rulesTupleList.unzip
      val matchingRule = List(rules.head)
      val normalizedRequest = List(rules.map(_.id).head)
      RateLimitRuleMatcher(() => rules, normalizedRequest) must_== matchingRule
    }
  }

  def serviceRuleMatch = prop {
    (serviceRuleTuple: (ServiceRule, Request)) => {
      val (serviceRule, request) = serviceRuleTuple
      RequestNormalization(_ => Some(serviceRule.target), _ => None, request) match {
        case serviceNameBucketId :: serviceNameAndPathBucketId =>
          RateLimitRuleMatcher.matchById(serviceRule, serviceNameAndPathBucketId.head) must_== true
        case Nil => (true must beFalse).setMessage(
          "In this test context, RequestNormalization should always return a list"
        )
      }
    }
  }

  def serviceRuleNoMatch = prop {
    (
      normalizedRequest: String,
      serviceRuleTuple: (ServiceRule, Request)
    ) => {
      val (serviceRule, request) = serviceRuleTuple
      RateLimitRuleMatcher.matchById(serviceRule, normalizedRequest) must_== false
    }
  }

  def subjectRuleMatch = prop {
    (
      subjectRuleTuple: (SubjectRule, Request),
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val (subjectRule, request) = subjectRuleTuple
      val entUuid = claimUuids._1
      val normalizedRequest = RequestNormalization(_ => None, _ => Some((entUuid, subjectRule.target)), request) match {
        case entUuid :: subUuid :: Nil => subUuid
        case _ => ""
      }
      RateLimitRuleMatcher.matchById(subjectRule, normalizedRequest) must_== true
    }
  }

  def subjectRuleNoMatch = prop {
    (
      normalizedRequest: String,
      subjectRuleTuple: (SubjectRule, Request)
    ) => {
      val (subjectRule, request) = subjectRuleTuple
      RateLimitRuleMatcher.matchById(subjectRule, normalizedRequest) must_== false
    }
  }

  def enterpriseRuleMatch = prop {
    (
      enterpriseRuleTuple: (EnterpriseRule, Request),
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val (enterpriseRule, request) = enterpriseRuleTuple
      val subUuid = claimUuids._2
      val normalizedRequest = RequestNormalization(_ => None, _ => Some((enterpriseRule.target, subUuid)), request) match {
        case entUuid :: subUuid :: Nil => entUuid
        case _ => ""
      }
      RateLimitRuleMatcher.matchById(enterpriseRule, normalizedRequest) must_== true
    }
  }

  def enterpriseRuleNoMatch = prop {
    (
      normalizedRequest: String,
      enterpriseRuleTuple: (EnterpriseRule, Request)
    ) => {
      val (enterpriseRule, request) = enterpriseRuleTuple
      RateLimitRuleMatcher.matchById(enterpriseRule, normalizedRequest) must_== false
    }
  }

}
