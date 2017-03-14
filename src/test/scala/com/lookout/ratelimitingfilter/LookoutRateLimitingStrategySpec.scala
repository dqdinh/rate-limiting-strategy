package com.lookout.ratelimitingfilter

import com.twitter.finagle.service.RateLimitingFilter
import com.twitter.finagle.http.{Request, Response, Status}
import org.specs2.{Specification, ScalaCheck}
import org.scalacheck.Gen
import com.twitter.util.{Await, Future, Duration => TDuration}
import com.twitter.finagle.{Service, RefusedByRateLimiter}
import io.circe.syntax._
import io.circe.jawn._
import com.lookout.ratelimitingfilter.models._

class LookoutRateLimitingStrategySpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
  LookoutRateLimitingStrategy case class
    when there is a Redis Error, rate limiting is switched off $redisErrorNoRateLimiting

    when an incoming request is under the rate limit threshold
      And the request is targeting a service
        it does not filter incoming requests                   $belowServiceRateLimitThreshold
      And the request is targeting a subject UUID
        it does not filter incoming requests                   $belowSubjectRateLimitThreshold
      And the request is targeting an ent UUID
        it does not filter incoming requests                   $belowEntRateLimitThreshold

    when an incoming request is over the rate limit threshold
      And the request is targeting an Ent UUID
        it responds with 429 status                            $overEntRateLimitThresholdStatus
        it renders correct JSON response                       $overEntRateLimitThresholdResponse
  """

  val service = Service.mk[Request, Response] {
    _ => Future { Response(Status.Ok) }
  }

  val handleError = new HandleRefusedByRateLimiterFilter

  def genServiceStrategy(
    rules: List[RateLimitRule],
    ruleAndRequest: (ServiceRule, Request),
    toggle: Boolean = true
  ): LookoutRateLimitingStrategy = {
    val rule = ruleAndRequest._1
    val request = ruleAndRequest._2
    new LookoutRateLimitingStrategy(
      request => Some(rule.target),
      _ => None,
      () => rules ++ List(rule),
      rule => toggle
    )
  }

  def genSubjectStrategy(
    rules: List[RateLimitRule],
    subjectRule: (SubjectRule, Request),
    entRule: (EnterpriseRule, Request),
    toggle: Boolean = true
  ): LookoutRateLimitingStrategy = {
    val entTarget = entRule._1.target
    val rule = subjectRule._1
    val request = subjectRule._2
    new LookoutRateLimitingStrategy(
      _ => None,
      request => Some((entTarget, rule.target)),
      () => rules ++ List(rule),
      rule => toggle
    )
  }

  def genEntStategy(
    rules: List[RateLimitRule],
    subjectRule: (SubjectRule, Request),
    entRule: (EnterpriseRule, Request),
    toggle: Boolean = true
  ): LookoutRateLimitingStrategy = {
    val subjectTarget = subjectRule._1.target
    val rule = entRule._1
    val request = entRule._2
    new LookoutRateLimitingStrategy(
      _ => None,
      request => Some((rule.target, subjectTarget)),
      () => rules ++ List(rule),
      rule => toggle
    )
  }

  def redisErrorNoRateLimiting = prop {
    (
      ruleAndRequest: (ServiceRule, Request),
      rules: List[RateLimitRule]
    ) => {
      val rule = ruleAndRequest._1
      val request = ruleAndRequest._2
      val strategy = new LookoutRateLimitingStrategy(
        request => Some(rule.target),
        _ => None,
        () => rules ++ List(rule),
        rule => throw RedisError(s"Redis batch operation failed for rule id: ${rule.id}")
      )
      val filter = new RateLimitingFilter[Request, Response](strategy(_))
      val rateLimitedService = filter andThen service

      Await.result(rateLimitedService(request).map(_.status), TDuration.Top) must_== (Status.Ok)
    }
  }

  def belowEntRateLimitThreshold = prop {
    (
      entRule: (EnterpriseRule, Request),
      subjectRule: (SubjectRule, Request),
      rules: List[RateLimitRule]
    ) => {
      val request = entRule._2
      val strategy = genEntStategy(rules, subjectRule, entRule)
      val filter = new RateLimitingFilter[Request, Response](strategy(_))
      val rateLimitedService = filter andThen service

      Await.result(rateLimitedService(request).map(_.status), TDuration.Top) must_== (Status.Ok)
    }
  }

  def belowSubjectRateLimitThreshold = prop {
    (
      entRule: (EnterpriseRule, Request),
      subjectRule: (SubjectRule, Request),
      rules: List[RateLimitRule]
    ) => {
      val request = subjectRule._2
      val strategy = genSubjectStrategy(rules, subjectRule, entRule)
      val filter = new RateLimitingFilter[Request, Response](strategy(_))
      val rateLimitedService = filter andThen service

      Await.result(rateLimitedService(request).map(_.status), TDuration.Top) must_== (Status.Ok)
    }
  }

  def belowServiceRateLimitThreshold = prop {
    (
      ruleAndRequest: (ServiceRule, Request),
      rules: List[RateLimitRule]
    ) => {
      val request = ruleAndRequest._2
      val strategy = genServiceStrategy(rules, ruleAndRequest)
      val filter = new RateLimitingFilter[Request, Response](strategy(_))
      val rateLimitedService = filter andThen service

      Await.result(rateLimitedService(request).map(_.status), TDuration.Top) must_== (Status.Ok)
    }
  }

  def overEntRateLimitThresholdStatus = prop {
    (
      entRule: (EnterpriseRule, Request),
      subjectRule: (SubjectRule, Request),
      rules: List[RateLimitRule]
    ) => {
      val request = entRule._2
      val strategy = genEntStategy(rules, subjectRule, entRule, false)
      val filter = new RateLimitingFilter[Request, Response](strategy(_))
      val rateLimitedService = handleError andThen filter andThen service

      Await.result(rateLimitedService(request).map(_.status), TDuration.Top) must_== (Status.TooManyRequests)
    }
  }

  def overEntRateLimitThresholdResponse = prop {
    (
      entRule: (EnterpriseRule, Request),
      subjectRule: (SubjectRule, Request),
      rules: List[RateLimitRule]
    ) => {
      val request = entRule._2
      val rateLimitedMessage = s"Request is rate limited: ${request.encodeString()}"
      val strategy = genEntStategy(rules, subjectRule, entRule, false)
      val filter = new RateLimitingFilter[Request, Response](strategy(_))
      val rateLimitedService = handleError andThen filter andThen service
      val response = Await.result(rateLimitedService(request), TDuration.Top)
      val contentJson: Either[io.circe.Error, RefusedByRateLimiterError] =
        decode[RefusedByRateLimiterError](response.contentString)

      contentJson.map(_.message) must_== Right(rateLimitedMessage)
    }
  }
}
