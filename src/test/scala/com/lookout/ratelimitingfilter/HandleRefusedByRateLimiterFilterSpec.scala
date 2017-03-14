package com.lookout.ratelimitingfilter

import com.twitter.finagle.service.RateLimitingFilter
import com.twitter.finagle.{Service, SimpleFilter, RefusedByRateLimiter}
import com.twitter.finagle.http.{Request, Response, Status}
import org.specs2.{Specification, ScalaCheck}
import org.scalacheck.Gen
import com.twitter.util.{Await, Future, Duration => TDuration}
import com.twitter.finagle.Service
import io.circe.syntax._
import io.circe.jawn._
import com.lookout.ratelimitingfilter.models._

class HandleRefusedByRateLimiterFilterSpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
  HandleRefusedByRateLimiterFilter case class
    when an incoming request throws
      a RefusedByRateLimiter error
        it responds with 429 status      $rateLimitedStatusResponse
        it renders correct JSON response $rateLimitedMessageResponse
      an error other than RefusedByRateLimiter
        it responds with 500 status      $otherErrorStatusResponse
  """

  case class ThrowExceptionFilter() extends SimpleFilter[Request, Response] {
    def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
      Future.exception(new Exception("error"))
    }
  }

  case class ThrowRefusedByRateLimiterFilter() extends SimpleFilter[Request, Response] {
    def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
      Future.exception(new RefusedByRateLimiter)
    }
  }

  val throwExceptionError = new ThrowExceptionFilter
  val throwRateLimitError = new ThrowRefusedByRateLimiterFilter
  val filter = new HandleRefusedByRateLimiterFilter
  val service = Service.mk[Request, Response] {
    _ => Future { Response(Status.Ok) }
  }

  def otherErrorStatusResponse = prop {
    (request: Request) => {
      val newService = filter andThen throwExceptionError andThen service
      Await.result(newService(request), TDuration.Top) must throwA[Exception](message = "error")
    }
  }

  def rateLimitedStatusResponse = prop {
    (request: Request) => {
      val newService = filter andThen throwRateLimitError andThen service
      Await.result(newService(request).map(_.status), TDuration.Top) must_== Status.TooManyRequests
    }
  }

  def rateLimitedMessageResponse = prop {
    (request: Request) => {
      val rateLimitedMessage = s"Request is rate limited: ${request.encodeString()}"
      val newService = filter andThen throwRateLimitError andThen service
      val response = Await.result(newService(request), TDuration.Top)
      val contentJson: Either[io.circe.Error, RefusedByRateLimiterError] =
        decode[RefusedByRateLimiterError](response.contentString)

      contentJson.map(_.message) must_== Right(rateLimitedMessage)
    }
  }
}
