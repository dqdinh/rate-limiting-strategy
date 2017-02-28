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
      "com.twitter" %% "finagle-http" % "6.42.0",
      "com.chuusai" %% "shapeless" % "2.3.2",
      "org.typelevel" %% "cats" % "0.9.0",
      "org.specs2" %% "specs2-core" % "3.8.8",
      "org.specs2" %% "specs2-scalacheck" % "3.8.8"
    )
  )
