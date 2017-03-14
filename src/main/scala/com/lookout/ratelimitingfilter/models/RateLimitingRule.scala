package com.lookout.ratelimitingfilter.models

import java.util.UUID
import java.net.URL
import com.twitter.util.Duration
import com.twitter.finagle.http.Method
import shapeless.tag._

// String and UUID tagged types

sealed trait ServiceName
sealed trait EntClaim
sealed trait SubClaim

// Rate Limiting Rule ADTs

sealed trait RateLimitRule {
  val threshold: Int;
  val period: Duration;
  val method: Method;
  val path: URL;
  val id: String;
}

final case class ServiceRule(
  val target: String @@ ServiceName,
  val threshold: Int,
  val period: Duration,
  val method: Method,
  val path: URL,
  val id: String
) extends RateLimitRule

final case class SubjectRule(
  val target: UUID @@ SubClaim,
  val threshold: Int,
  val period: Duration,
  val method: Method,
  val path: URL,
  val id: String
) extends RateLimitRule

final case class EnterpriseRule(
  val target: UUID @@ EntClaim,
  val threshold: Int,
  val period: Duration,
  val method: Method,
  val path: URL,
  val id: String
) extends RateLimitRule

// Rate Limiting Rule extractors

object ServiceRuleId {
  def unapply(rule: ServiceRule): Option[String] = Some(rule.id)
}

object SubjectRuleId {
  def unapply(rule: SubjectRule): Option[String] = Some(rule.id)
}

object EnterpriseRuleId {
  def unapply(rule: EnterpriseRule): Option[String] = Some(rule.id)
}
