inThisBuild(
  List(
    onChangedBuildSource := ReloadOnSourceChanges,
    organization := "com.typedlabs",
    homepage := Some(url("https://github.com/typedlabs/fs2-nats/")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/typedlabs/fs2-nats"),
        "scm:git:git@github.com:typedlabs/fs2-nats.git"
      )
    ),
    licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
    developers := List(
      Developer("jsilva", "Joao Da Silva", "joao@typedlabs.com", url("https://typedlabs.com"))
    )
  )
)

lazy val publishingSettings = Seq(
  bintrayReleaseOnPublish := true,
  bintrayOrganization := Some("typedlabs"),
  bintrayPackageLabels := Seq("scala", "fs2", "nats", "pubsub", "streams", "streaming"),
  bintrayVcsUrl := Some("https://github.com/typedlabs/fs2-nats"),
  pomIncludeRepository := { _ =>
    false
  }
)

lazy val releaseSettings = Seq(
  pgpPublicRing := file("./travis/local.pubring.asc"),
  pgpSecretRing := file("./travis/local.secring.asc"),
  releaseEarlyWith := BintrayPublisher
)

lazy val `fs2-nats` = (project in file("."))
  .settings(publishingSettings)
  .settings(releaseSettings)
  .settings(
    name := "fs2-nats",
    scalaVersion := "2.12.9",
    crossScalaVersions := Seq("2.12.9", "2.13.1"),
    libraryDependencies ++= Seq(
      // Streaming
      "co.fs2" %% "fs2-core" % "2.0.0",
      "co.fs2" %% "fs2-io" % "2.0.0",
      // Protocol parsing
      "org.scodec" %% "scodec-core" % "1.11.4",
      "org.scodec" %% "scodec-bits" % "1.1.12",
      "org.scodec" %% "scodec-stream" % "2.0.0",
      // Json
      "io.circe" %% "circe-core" % "0.12.1",
      "io.circe" %% "circe-generic" % "0.12.1",
      "io.circe" %% "circe-parser" % "0.12.1",
      "io.circe" %% "circe-generic-extras" % "0.12.2",
      // Logging
      "io.chrisdavenport" %% "log4cats-core" % "1.0.1",
      "io.chrisdavenport" %% "log4cats-slf4j" % "1.0.1",
      "ch.qos.logback" % "logback-classic" % "1.2.3"
    ),
    commands ++= Seq(compileWithMacroParadise)
  )

def compileWithMacroParadise: Command = Command.command("compileWithMacroParadise") { state =>
  import Project._
  val extractedState = extract(state)
  val stateWithMacroParadise = CrossVersion.partialVersion(extractedState.get(scalaVersion)) match {
    case Some((2, n)) if n >= 13 =>
      extractedState.appendWithSession(Seq(Compile / scalacOptions += "-Ymacro-annotations"), state)
    case _ =>
      extractedState.appendWithSession(
        addCompilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)),
        state
      )
  }
  val (stateAfterCompileWithMacroParadise, _) =
    extract(stateWithMacroParadise).runTask(Compile / compile, stateWithMacroParadise)
  stateAfterCompileWithMacroParadise
}

addCommandAlias("compile", "; compileWithMacroParadise")
addCommandAlias("fmt", "; compile:scalafmt; test:scalafmt; scalafmtSbt")
addCommandAlias("fmtCheck", "; compile:scalafmtCheck; test:scalafmtCheck; scalafmtSbtCheck")
