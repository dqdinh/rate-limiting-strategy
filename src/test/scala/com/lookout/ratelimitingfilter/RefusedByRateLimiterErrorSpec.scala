package com.lookout.ratelimitingfilter

import com.twitter.finagle.http.{Request, Status}
import org.specs2.{Specification, ScalaCheck}
import io.circe.syntax._
import io.circe.jawn._
import com.lookout.ratelimitingfilter.models._

class RefusedByRateLimiterErrorSpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
    RefusedByRateLimiterError object
      it should not lose data on roundtrips to JSON $dataIntegrity
      it should contain a `message` field           $messageField
      it should create a response with 429 status   $statusCode
  """

  def fields(error: RefusedByRateLimiterError): Seq[String] =
    error.asJson.asObject.toList.flatMap(_.fields)

  def dataIntegrity = prop {
    (error: RefusedByRateLimiterError) => {
      (decode[RefusedByRateLimiterError](error.asJson.noSpaces)) must_== Right(error)
    }
  }

  def messageField = prop {
    (error: RefusedByRateLimiterError) => {
      fields(error) must contain("message")
    }
  }

  def statusCode = prop {
    (error: RefusedByRateLimiterError) => {
      error.toResponse.status must_== Status.TooManyRequests
    }
  }
}
