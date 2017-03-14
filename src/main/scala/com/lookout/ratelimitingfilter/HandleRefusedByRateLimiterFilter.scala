package com.lookout.ratelimitingfilter

import com.twitter.finagle.http.{Request, Response}
import com.twitter.finagle.{Failure, Service, SimpleFilter, RefusedByRateLimiter}
import com.twitter.util.Future

/** A filter that catches Finagle's RefusedByRateLimiter exception and
  * converts it to the appropriate HTTP response. Used in conjunction
  * with Finagle's RateLimitingFilter and the LookoutRateLimitingStrategy.
  */
case class HandleRefusedByRateLimiterFilter() extends SimpleFilter[Request, Response] {
  def apply(request: Request, service: Service[Request, Response]): Future[Response] = Future {
    service(request) handle {
      case _: RefusedByRateLimiter => {
        val rateLimitedMessage = s"Request is rate limited: ${request.encodeString()}"
        RefusedByRateLimiterError(rateLimitedMessage).toResponse
      }
    }
  }.flatMap(identity _)
}
