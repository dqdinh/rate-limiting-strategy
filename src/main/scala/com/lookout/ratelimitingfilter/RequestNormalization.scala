package com.lookout.ratelimitingfilter

import java.util.UUID
import java.net.URLEncoder
import com.twitter.finagle.http.Request
import shapeless.tag._
import cats._
import cats.data._
import cats.implicits._

/** Each request should map to a canonical bucket name. The
  * bucket name is constructed from the method, path, and either
  * a Lookout identity UUID or name.
  *
  * NOTE The following rules are used for string standardization:
  * (1) `::` as a namespace delimiter
  * (2) The request path is URL encoded to prevent issues parsing `:`
  *     and lowercased.
  *
  * There are currently three types of bucket names:
  *
  * Service bucket    - <METHOD>::<PATH>::<Service Name>
  * Subject bucket    - <METHOD>::<PATH>::<Subject UUID>
  * Enterprise bucket - <METHOD>::<PATH>::<Enterprise UUID>
  */
object RequestNormalization {
  def apply(
    serviceLookup: Request => Option[String @@ Service],
    claimLookup: Request => Option[(UUID @@ EntClaim, UUID @@ SubClaim)],
    request: Request
  ): List[String] = {
    val method = request.method
    val path = URLEncoder.encode(request.path, "UTF-8").toLowerCase
    val serviceBucket: Option[List[String]] = serviceLookup(request).map {
      serviceName => s"$method::$path::$serviceName" :: Nil
    }
    val idBuckets: Option[List[String]] = claimLookup(request).map {
      case (entUuid, subUuid) =>
        s"$method::$path::$entUuid" :: s"$method::$path::$subUuid" :: Nil
    }

    (serviceBucket |+| idBuckets).getOrElse(Nil)
  }
}
