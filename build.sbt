lazy val BUILD_NUMBER = sys.env.getOrElse("BUILD_NUMBER", "DEV")

lazy val Version = "0.0.1+$BUILD_NUMBER"

lazy val root = (project in file(".")).
  settings(
    resolvers += "Artifactory" at "https://artifactory.prod.lkt.is/artifactory/repo/",
    inThisBuild(List(
                  organization := "com.lookout",
                  scalaVersion := "2.12.1",
                  version := Version
                )),
    name := "rate-limiting-filter",
    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.1.7",
      "com.typesafe.scala-logging" %% "scala-logging" % "3.5.0",
      "com.twitter" %% "finagle-http" % "6.42.0",
      "com.chuusai" %% "shapeless" % "2.3.2",
      "org.typelevel" %% "cats" % "0.9.0",
      "org.specs2" %% "specs2-core" % "3.8.8",
      "org.specs2" %% "specs2-scalacheck" % "3.8.8",
      "net.debasishg" %% "redisclient" % "3.4",
      "io.circe" %% "circe-core" % "0.7.0",
      "io.circe" %% "circe-generic" % "0.7.0",
      "io.circe" %% "circe-parser" % "0.7.0"
    ),
    // Scalac options source - https://tpolecat.github.io/2014/04/11/scalac-flags.html
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding", "UTF-8",
      "-feature",
      "-language:existentials",
      "-language:higherKinds",
      "-language:implicitConversions",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xlint",
      "-Yno-adapted-args",
      "-Ywarn-dead-code",     // N.B. doesn't work well with the ??? hole
      "-Ywarn-value-discard"
    )
  )

lazy val example = (project in file("example")).dependsOn(root)
