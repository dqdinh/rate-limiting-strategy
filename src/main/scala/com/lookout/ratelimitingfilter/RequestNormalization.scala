package com.lookout.ratelimitingfilter

import java.nio.charset.StandardCharsets
import java.util.UUID
import java.net.URLEncoder
import com.twitter.finagle.http.Request
import shapeless.tag._
import cats.implicits._
import com.lookout.ratelimitingfilter.models._

/** Each request should map to a canonical bucket name. The
  * bucket name is constructed from the method, path, and either
  * a Lookout identity UUID or name.
  *
  * NOTE The following rules are used for string standardization:
  * (1) `::` as a namespace delimiter
  * (2) The request path is URL encoded to prevent issues parsing `:`
  *     and lowercased.
  *
  * For each request, `RequestNormalization` creates four types of bucket names
  * in list:
  *
  * 1. Service Name bucket - <METHOD>::<Service Name>
  * 2. Service bucket      - <METHOD>::<PATH>::<Service Name>
  * 3. Subject bucket      - <METHOD>::<PATH>::<Subject UUID>
  * 4. Enterprise bucket   - <METHOD>::<PATH>::<Enterprise UUID>
  *
  * NOTE We use tagged types to distinguish ServiceName Strings
  * and different types of UUIDs.
  */
object RequestNormalization {
  def apply(
    serviceLookup: Request => Option[String @@ ServiceName],
    claimLookup: Request => Option[(UUID @@ EntClaim, UUID @@ SubClaim)],
    request: Request
  ): List[String] = {
    val method = request.method
    val path = encodePath(request.path)
    val serviceBuckets: Option[List[String]] = serviceLookup(request).map {
      serviceName => s"$method::$serviceName" :: s"$method::$path::$serviceName" :: Nil
    }
    val idBuckets: Option[List[String]] = claimLookup(request).map {
      case (entUuid, subUuid) =>
        s"$method::$path::$entUuid" :: s"$method::$path::$subUuid" :: Nil
    }

    (serviceBuckets |+| idBuckets).getOrElse(Nil)
  }

  def encodePath(path: String): String =
    URLEncoder.encode(path, StandardCharsets.UTF_8.toString).toLowerCase
}
