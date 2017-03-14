package com.lookout.example

import com.twitter.finagle.builder.{Server, ServerBuilder}
import com.twitter.finagle.http._
import com.twitter.finagle.http.filter.ExceptionFilter
import com.twitter.finagle.service.RateLimitingFilter
import com.twitter.finagle.{Service, RefusedByRateLimiter}
import com.twitter.util.Future
import java.net.InetSocketAddress
import shapeless.tag
import shapeless.tag._
import com.redis._
import java.net.URL
import com.twitter.util.{Duration, Future, Stopwatch}
import com.lookout.ratelimitingfilter.{RedisOps, LeakyBucket, HandleRefusedByRateLimiterFilter, LookoutRateLimitingStrategy}
import com.lookout.ratelimitingfilter.models._

object HttpServer {
  def main(args: Array[String]) {
    val RedisInstance = new RedisClient("localhost", 6379)

    /** The service itself. Simply echos back a String.
      */
    val okResponse = Service.mk[Request, Response] {
      _ => Future {
        val response = Response(Version.Http11, Status.Ok)
        response.contentString = "Winner winner, chicken dinner! Rate limiting was not triggered."
        response
      }
    }

    // Mock data
    val url = new URL("http://localhost:8080/les")
    val path = url.getPath
    val serviceName = tag[ServiceName]("les")

    // Mock Rules
    // First rule: overall rate limit on Service name with 10 requests / 60 seconds
    val serviceNameRule = ServiceRule(serviceName, 10, Duration.fromSeconds(60), Method.Get, url, s"GET::${serviceName}")
    // Second rule: API rate limit on Service name and `/les` with 5 requests / 60 seconds
    val serviceApiRule = ServiceRule(serviceName, 5, Duration.fromSeconds(60), Method.Get, url, s"GET::%2fles::${serviceName}")

    // Strategy functions
    val serviceLookup = (req: Request) => Some(serviceName)
    val claimLookup = (req: Request) => None
    val rulesLookup = () => List(serviceNameRule, serviceApiRule)
    val runRedisOps = (rule: RateLimitRule) => RedisOps.processRateLimitRule(RedisInstance, rule)
    val leakAndIncFn = (rule: RateLimitRule) => LeakyBucket.leakAndIncBucketTokens(rule, runRedisOps)
    val processRule = (rule: RateLimitRule) => LeakyBucket.processRule(rule, leakAndIncFn)

    // Stategy
    val rateLimitStrategy = new LookoutRateLimitingStrategy(
      serviceLookup,
      claimLookup,
      rulesLookup,
      processRule
    )

    // Filters
    val handleRefusedByRateLimiter = new HandleRefusedByRateLimiterFilter
    val rateLimitFilter = new RateLimitingFilter[Request, Response](
      req => rateLimitStrategy(req)
    )

    // compose the Filters and Service together:
    val myService: Service[Request, Response] = handleRefusedByRateLimiter andThen rateLimitFilter andThen okResponse

    val server: Server = ServerBuilder()
      .codec(Http())
      .bindTo(new InetSocketAddress(8080))
      .name("httpserver")
      .build(myService)
  }
}
