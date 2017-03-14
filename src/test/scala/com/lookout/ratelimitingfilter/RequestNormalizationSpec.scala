package com.lookout.ratelimitingfilter

import java.util.UUID
import java.net.URLEncoder
import com.twitter.finagle.http.{Method, Request}
import org.specs2.{Specification, ScalaCheck}
import shapeless.tag._
import com.lookout.ratelimitingfilter.models._

class RequestNormalizationSpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
  The RequestNormalization object
    should create bucket ids that start with the method                   $method
    should create a service name bucket id that does not contain the path $serviceNameBucketId
    should create bucket ids that contain the URL encoded path            $encodedPathBucketIds
    should create bucket ids seperated by "::"                            $separator

    when there is a service name mapping
      it should create one bucket name containing the service name        $oneServiceName

    when there is a claims mapping
      it should create one bucket name containing the Enterprise UUID     $oneEnterpriseUuid
      it should create one bucket name containing the Subject UUID        $oneSubjectUuid

    when there is no service name mapping
      it should not create a bucket name containing the service name      $noServiceName

    when there is no claims mapping
      it should not create a bucket name containing the Enterprise UUID   $noEnterpriseUuid
      it should not create a bucket name containing the Subject UUID      $noSubjectUuid

    when there is a service name and a claims mapping
      it should create a list with four bucket ids                        $fourBucketIds

    when there is no service name mapping but there is a claims mapping
      it should create a list with two claim bucket ids                   $twoClaimBucketIds

    when there is a service name mapping but no claims mapping
      it should create a list with two service bucket ids                 $twoServiceBucketIds

    when there is no service name mapping and no claims mapping
      it should create an emtpy list                                      $zeroBucketIds
  """

  def method = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val method = request.method.toString
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUuids), request)
        .map(_ must startWith(s"$method::"))
        .reduce(_ and _)
    }
  }

  def serviceNameBucketId = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val path = RequestNormalization.encodePath(request.path)
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUuids), request) match {
        case serviceNameBucketId :: pathBucketIds =>
          serviceNameBucketId must_== s"${request.method}::$serviceName"
        case Nil => (true must beFalse).setMessage(
          "In this test context, RequestNormalization should always return a list"
        )
      }
    }
  }

  def encodedPathBucketIds = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val path = RequestNormalization.encodePath(request.path)
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUuids), request) match {
        case globalServiceBucketId :: pathBucketIds =>
          pathBucketIds.map(_ must contain(s"::$path::")).reduce(_ and _)
        case Nil => (true must beFalse).setMessage(
          "In this test context, RequestNormalization should always return a list"
        )
      }
    }
  }

  def separator = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val delimiterPattern = """(.*)::(.*::)?(.*)""".r
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUuids), request)
        .map(_ must beMatching(delimiterPattern))
        .reduce(_ and _)
    }
  }

  def oneServiceName = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: Option[(UUID @@ EntClaim, UUID @@ SubClaim)]
    ) => {
      RequestNormalization(_ => Some(serviceName), _ => claimUuids, request)
        .map(_ must endWith(s"::$serviceName"))
        .reduce(_ or _)
    }
  }

  def oneEnterpriseUuid = prop {
    (
      request: Request,
      serviceName: Option[String @@ ServiceName],
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val entUuid = claimUuids._1.toString
      RequestNormalization(_ => serviceName, _ => Some(claimUuids), request)
        .map(_ must endWith(s"::$entUuid"))
        .reduce(_ or _)
    }
  }

  def oneSubjectUuid = prop {
    (
      request: Request,
      serviceName: Option[String @@ ServiceName],
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val subUuid = claimUuids._2.toString
      RequestNormalization(_ => serviceName, _ => Some(claimUuids), request)
        .map(_ must endWith(s"::$subUuid"))
        .reduce(_ or _)
    }
  }

  def noServiceName = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      RequestNormalization(_ => None, _ => Some(claimUuids), request)
        .map(_ must not endWith(s"::$serviceName"))
        .reduce(_ and _)
    }
  }

  def noEnterpriseUuid = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val entUuid = claimUuids._1.toString
      RequestNormalization(_ => Some(serviceName), _ => None, request)
        .map(_ must not endWith(s"::$entUuid"))
        .reduce(_ and _)
    }
  }

  def noSubjectUuid = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val subUuid = claimUuids._2.toString
      RequestNormalization(_ => Some(serviceName), _ => None, request)
        .map(_ must not endWith(s"::$subUuid"))
        .reduce(_ and _)
    }
  }

  def fourBucketIds = prop {
    (
      request: Request,
      serviceName: String @@ ServiceName,
      claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUuids), request) must have size(4)
    }
  }

  def twoClaimBucketIds = prop {
    (request: Request, claimUuids: (UUID @@ EntClaim, UUID @@ SubClaim)) => {
      RequestNormalization(_ => None, _ => Some(claimUuids), request) must have size(2)
    }
  }

  def twoServiceBucketIds = prop {
    (request: Request, serviceName: String @@ ServiceName) => {
      RequestNormalization(_ => Some(serviceName), _ => None, request) must have size(2)
    }
  }

  def zeroBucketIds = prop { (request: Request) =>
    RequestNormalization(_ => None, _ => None, request) must beEmpty
  }
}
