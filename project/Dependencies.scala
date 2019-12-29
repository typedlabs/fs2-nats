import sbt._

object Dependencies {

  object Circe {
    val core = "io.circe" %% "circe-core" % "0.12.1"
    val generic = "io.circe" %% "circe-generic" % "0.12.1"
    val parser = "io.circe" %% "circe-parser" % "0.12.1"
    val genericExtras = "io.circe" %% "circe-generic-extras" % "0.12.2"
  }

  object Scodec {
    val core = "org.scodec" %% "scodec-core" % "1.11.4"
    val bits = "org.scodec" %% "scodec-bits" % "1.1.12"
    val stream = "org.scodec" %% "scodec-stream" % "2.0.0"
  }

  object Fs2 {
    val core = "co.fs2" %% "fs2-core" % "2.0.0"
    val io = "co.fs2" %% "fs2-io" % "2.0.0"
  }

  object Testing {
    val scalaTest = "org.scalatest" %% "scalatest" % "3.1.0"
    val mockitoScalatest = "org.mockito" %% "mockito-scala-scalatest" % "1.10.2"
  }

  object Log4Cats {
    val core = "io.chrisdavenport" %% "log4cats-core" % "1.0.1"
    val slf4j = "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1"
  }

  object Logging {
    val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  }

}
