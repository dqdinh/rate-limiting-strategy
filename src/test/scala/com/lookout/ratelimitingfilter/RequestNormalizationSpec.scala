package com.lookout.ratelimitingfilter

import java.util.UUID
import java.net.URLEncoder
import com.twitter.finagle.http.{Method, Request}
import org.specs2.{Specification, ScalaCheck}
import shapeless.tag._

class RequestNormalizationSpec extends Specification with ScalaCheck with Arbitraries {
  def is = s2"""
  The RequestNormalization object
    should create bucket names that start with the method               $method
    should create bucket names that contain the URL encoded path        $path
    should create bucket names seperated by "::"                        $separator

    when there is a service name mapping
      it should create one bucket name containing the service name      $oneServiceName

    when there is a claims mapping
      it should create one bucket name containing the Enterprise UUID   $oneEnterpriseUuid
      it should create one bucket name containing the Subject UUID      $oneSubjectUuid

    when there is no service name mapping
      it should not create a bucket name containing the service name    $noServiceName

    when there is no claims mapping
      it should not create a bucket name containing the Enterprise UUID $noEnterpriseUuid
      it should not create a bucket name containing the Subject UUID    $noSubjectUuid

    when there is a service name and a claims mapping
      it should create a list with three bucket names                   $threeBucketNames

    when there is no service name mapping but there is a claims mapping
      it should create a list with two bucket names                     $twoBucketNames

    when there is a service name mapping but no claims mapping
      it should create a list with one bucket name                      $oneBucketName

    when there is no service name mapping and no claims mapping
      it should create an emtpy list                                    $zeroBucketNames
  """

  def method = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val method = request.method.toString
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUUIDs), request)
        .map(_ must startWith(s"$method::"))
        .reduce(_ and _)
    }
  }

  def path = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val path = URLEncoder.encode(request.path, "UTF-8").toLowerCase
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUUIDs), request)
        .map(_ must contain(s"::$path::"))
        .reduce(_ and _)
    }
  }

  def separator = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val delimiterPattern = """(.*)::(.*)::(.*)""".r
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUUIDs), request)
        .map(_ must beMatching(delimiterPattern))
        .reduce(_ and _)
    }
  }

  def oneServiceName = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: Option[(UUID @@ EntClaim, UUID @@ SubClaim)]
    ) => {
      RequestNormalization(_ => Some(serviceName), _ => claimUUIDs, request)
        .map(_ must endWith(s"::$serviceName"))
        .reduce(_ or _)
    }
  }

  def oneEnterpriseUuid = prop {
    (
      request: Request,
      serviceName: Option[String @@ Service],
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val entUUID = claimUUIDs._1.toString
      RequestNormalization(_ => serviceName, _ => Some(claimUUIDs), request)
        .map(_ must endWith(s"::$entUUID"))
        .reduce(_ or _)
    }
  }

  def oneSubjectUuid = prop {
    (
      request: Request,
      serviceName: Option[String @@ Service],
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val subUUID = claimUUIDs._2.toString
      RequestNormalization(_ => serviceName, _ => Some(claimUUIDs), request)
        .map(_ must endWith(s"::$subUUID"))
        .reduce(_ or _)
    }
  }

  def noServiceName = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      RequestNormalization(_ => None, _ => Some(claimUUIDs), request)
        .map(_ must not endWith(s"::$serviceName"))
        .reduce(_ and _)
    }
  }

  def noEnterpriseUuid = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val entUUID = claimUUIDs._1.toString
      RequestNormalization(_ => Some(serviceName), _ => None, request)
        .map(_ must not endWith(s"::$entUUID"))
        .reduce(_ and _)
    }
  }

  def noSubjectUuid = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      val subUUID = claimUUIDs._2.toString
      RequestNormalization(_ => Some(serviceName), _ => None, request)
        .map(_ must not endWith(s"::$subUUID"))
        .reduce(_ and _)
    }
  }

  def threeBucketNames = prop {
    (
      request: Request,
      serviceName: String @@ Service,
      claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)
    ) => {
      RequestNormalization(_ => Some(serviceName), _ => Some(claimUUIDs), request) must have size(3)
    }
  }

  def twoBucketNames = prop {
    (request: Request, claimUUIDs: (UUID @@ EntClaim, UUID @@ SubClaim)) => {
      RequestNormalization(_ => None, _ => Some(claimUUIDs), request) must have size(2)
    }
  }

  def oneBucketName = prop {
    (request: Request, serviceName: String @@ Service) => {
      RequestNormalization(_ => Some(serviceName), _ => None, request) must have size(1)
    }
  }

  def zeroBucketNames = prop { (request: Request) =>
    RequestNormalization(_ => None, _ => None, request) must beEmpty
  }
}
