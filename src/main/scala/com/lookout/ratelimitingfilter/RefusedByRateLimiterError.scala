package com.lookout.ratelimitingfilter

import com.twitter.finagle.http.{Response, Status}
import com.twitter.finagle.RefusedByRateLimiter
import com.twitter.io.Buf
import com.twitter.logging.Logger
import io.circe.{Decoder, Encoder}
import io.circe.syntax._

final case class RefusedByRateLimiterError(
  message: String
) extends Exception(message) {
  def toResponse: Response = RefusedByRateLimiterError.toResponse(this)
}

object RefusedByRateLimiterError {
  val LOG = Logger.get(getClass)

  implicit val errorEncoder: Encoder[RefusedByRateLimiterError] =
    Encoder.forProduct1("message") { err => err.message }

  implicit val errorDecoder: Decoder[RefusedByRateLimiterError] =
    Decoder.forProduct1[String, RefusedByRateLimiterError]("message") {
      case (message: String) => RefusedByRateLimiterError(message)
    }

  def toResponse(error: RefusedByRateLimiterError): Response = {
    val response = Response(Status.TooManyRequests)
    val content = error.asJson.noSpaces
    LOG.info(content)
    response.content = Buf.Utf8(content)
    response.contentType = "application/json"
    response
  }
}
