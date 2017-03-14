package com.lookout.ratelimitingfilter

import java.net.URL
import java.net.URLEncoder
import java.util.UUID
import com.twitter.finagle.http.{Method, Status, Request}
import com.twitter.util.Duration
import org.scalacheck.{Gen, Arbitrary}
import shapeless.{tag}
import shapeless.tag._
import com.lookout.ratelimitingfilter.models._

trait Arbitraries {
  val AUTH_HEADER_NAME = "Authorization"

  val genHttpMethod = for {
    method <- Gen.oneOf(
      Method.Get,
      Method.Put,
      Method.Delete,
      Method.Post,
      Method.Patch
    )
  } yield method

  val genUrl = for {
    scheme <- Gen.oneOf("http", "https")
    domainName <- Gen.alphaStr
    suffix <- Gen.oneOf("org", "com", "net")
    path <- Gen.listOf(Gen.alphaStr).map(_.mkString("/"))
  } yield new URL(s"$scheme://$domainName.$suffix/$path")

  val genHeader = for {
    headerName <- Gen.alphaStr
    headerValue <- Gen.alphaStr
  } yield (headerName, headerValue)

  val genRequest: Gen[Request] = for {
    url <- genUrl
    header <- genHeader
    method <- genHttpMethod
  } yield {
    val request = Request(method, url.toString)
    request.headerMap.add(header._1, header._2)
    request
  }

  val genServiceName: Gen[String @@ ServiceName] = for {
    serviceName <- Gen.alphaStr.suchThat(!_.isEmpty)
  } yield tag[ServiceName](serviceName)

  val genClaimUUIDs: Gen[(UUID @@ EntClaim, UUID @@ SubClaim)] = for {
    entClaim <- Gen.uuid
    subClaim <- Gen.uuid
  } yield (tag[EntClaim](entClaim), tag[SubClaim](subClaim))

  // TODO DRY RateLimitRule generation
  // NOTE In order to pass on request state, RateLimitRules generators produce
  //      a tuple of (RateLimitRule, Request)
  val genServiceRule: Gen[(ServiceRule, Request)] = for {
    request <- genRequest
    target <- genServiceName
    threshold <- Gen.posNum[Int]
    seconds <- Gen.posNum[Int]
  } yield {
    val period = Duration.fromSeconds(seconds)
    val method = request.method
    val path = new URL(request.path)
    val encodedPath = RequestNormalization.encodePath(request.path)
    val id = s"${method.toString}::${encodedPath}::$target"
    (ServiceRule(target, threshold, period, method, path, id), request)
  }

  val genSubjectRule: Gen[(SubjectRule, Request)] = for {
    request <- genRequest
    (_, target) <- genClaimUUIDs
    threshold <- Gen.posNum[Int]
    seconds <- Gen.posNum[Int]
  } yield {
    val period = Duration.fromSeconds(seconds)
    val method = request.method
    val path = new URL(request.path)
    val encodedPath = RequestNormalization.encodePath(request.path)
    val id = s"${method.toString}::${encodedPath}::$target"
    (SubjectRule(target, threshold, period, method, path, id), request)
  }

  val genEnterpriseRule: Gen[(EnterpriseRule, Request)] = for {
    request <- genRequest
    (target, _) <- genClaimUUIDs
    threshold <- Gen.posNum[Int]
    seconds <- Gen.posNum[Int]
  } yield {
    val period = Duration.fromSeconds(seconds)
    val method = request.method
    val path = new URL(request.path)
    val encodedPath = RequestNormalization.encodePath(request.path)
    val id = s"${method.toString}::${encodedPath}::$target"
    (EnterpriseRule(target, threshold, period, method, path, id), request)
  }

  val genRateLimitRuleList: Gen[List[(RateLimitRule, Request)]] = for {
    n <- Gen.choose(1, 10)
    serviceRules <- Gen.listOfN(n, genServiceRule)
    subjectRules <- Gen.listOfN(n, genSubjectRule)
    enterpriseRules <- Gen.listOfN(n, genEnterpriseRule)
  } yield List(serviceRules, subjectRules, enterpriseRules).flatten

  val genRateLimitRule: Gen[RateLimitRule] = for {
    (serviceRule, _) <- genServiceRule
    (subjectRule, _) <- genSubjectRule
    (enterpriseRule, _) <- genEnterpriseRule
    rule <- Gen.oneOf(serviceRule, subjectRule, enterpriseRule)
  } yield rule

  val genListOfRedisOps: Gen[List[Any]] = for {
    n <- Gen.choose(1, 10)
    ops <- Gen.listOfN(n, Gen.alphaStr)
  } yield ops

  val genRefusedByRateLimiterError: Gen[RefusedByRateLimiterError] = for {
    message <- Gen.alphaStr
  } yield new RefusedByRateLimiterError(message)

  implicit val arbitraryRefusedByRateLimiterError: Arbitrary[RefusedByRateLimiterError] =
    Arbitrary(genRefusedByRateLimiterError)
  implicit val arbitraryListOfRedisOps: Arbitrary[List[Any]] = Arbitrary(genListOfRedisOps)
  implicit val arbitraryRateLimitRule: Arbitrary[RateLimitRule] = Arbitrary(genRateLimitRule)
  implicit val arbitraryRateLimitRuleList: Arbitrary[List[(RateLimitRule, Request)]] =
    Arbitrary(genRateLimitRuleList)
  implicit val arbitraryServiceRule: Arbitrary[(ServiceRule, Request)] = Arbitrary(genServiceRule)
  implicit val arbitrarySubjectRule: Arbitrary[(SubjectRule, Request)] = Arbitrary(genSubjectRule)
  implicit val arbitraryEnterpriseRule: Arbitrary[(EnterpriseRule, Request)] = Arbitrary(genEnterpriseRule)
  implicit val arbitraryClaimUUIDs: Arbitrary[(UUID @@ EntClaim, UUID @@ SubClaim)] = Arbitrary(genClaimUUIDs)
  implicit val arbitraryServiceName: Arbitrary[String @@ ServiceName] = Arbitrary(genServiceName)
  implicit val arbitraryHttpMethod: Arbitrary[Method] = Arbitrary(genHttpMethod)
  implicit val arbitraryURL: Arbitrary[URL] = Arbitrary(genUrl)
  implicit val arbitraryHeader: Arbitrary[(String, String)] = Arbitrary(genHeader)
  implicit val arbitraryRequest: Arbitrary[Request] = Arbitrary(genRequest)
}

object Arbitraries extends Arbitraries
