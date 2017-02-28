package com.lookout.ratelimitingfilter

import java.net.URL
import java.util.UUID
import com.twitter.finagle.http.{Method, Request}
import org.scalacheck.{Gen, Arbitrary}
import shapeless.{tag}
import shapeless.tag._

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

  val genServiceName: Gen[String @@ Service] = for {
    serviceName <- Gen.alphaStr.suchThat(!_.isEmpty)
  } yield tag[Service](serviceName)

  val genClaimUUIDs: Gen[(UUID @@ EntClaim, UUID @@ SubClaim)] = for {
    entClaim <- Gen.uuid
    subClaim <- Gen.uuid
  } yield (tag[EntClaim](entClaim), tag[SubClaim](subClaim))

  implicit val arbitraryClaimUUIDs: Arbitrary[(UUID @@ EntClaim, UUID @@ SubClaim)] = Arbitrary(genClaimUUIDs)
  implicit val arbitraryServiceName: Arbitrary[String @@ Service] = Arbitrary(genServiceName)
  implicit val arbitraryHttpMethod: Arbitrary[Method] = Arbitrary(genHttpMethod)
  implicit val arbitraryURL: Arbitrary[URL] = Arbitrary(genUrl)
  implicit val arbitraryHeader: Arbitrary[(String, String)] = Arbitrary(genHeader)
  implicit val arbitraryRequest: Arbitrary[Request] = Arbitrary(genRequest)
}

object Arbitraries extends Arbitraries
