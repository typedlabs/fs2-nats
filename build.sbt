lazy val scala212               = "2.12.9"
lazy val scala213               = "2.13.1"
lazy val supportedScalaVersions = List(scala212, scala213)

lazy val scalaSettings = Seq(
  scalaVersion := scala212,
  scalacOptions ++= scalacOptionsFor(scalaVersion.value),
  scalacOptions.in(Compile, console) ~= filterConsoleScalacOptions,
  scalacOptions.in(Test, console) ~= filterConsoleScalacOptions,
  // crossScalaVersions := supportedScalaVersions,
  libraryDependencies ++= Seq(
    Dependencies.Testing.scalaTest        % Test,
    Dependencies.Testing.mockitoScalatest % Test
  )
)

lazy val commonSettings = Seq(
  name := "fs2-nats",
  organization := "com.typedlabs",
  description := "Library nats.io based on fs2",
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  version := sys.env.getOrElse("TRAVIS_TAG", "0.1-SNAPSHOT"),
  bintrayReleaseOnPublish := true,
  bintrayOrganization := Some("typedlabs"),
  bintrayPackageLabels := Seq("scala", "fs2", "nats", "pubsub", "streams", "streaming"),
  bintrayVcsUrl := Some("https://github.com/typedlabs/fs2-nats"),
  publishArtifact in Test := false,
  testOptions += Tests.Argument(TestFrameworks.JUnit),
  onChangedBuildSource := ReloadOnSourceChanges,
  homepage := Some(url("https://github.com/typedlabs/fs2-nats/")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/typedlabs/fs2-nats"),
      "scm:git:git@github.com:typedlabs/fs2-nats.git"
    )
  ),
  developers := List(
    Developer("jsilva", "Joao Da Silva", "joao@typedlabs.com", url("https://typedlabs.com"))
  )
)


lazy val `fs2-nats` = (project in file("."))
  .settings(commonSettings)
  .settings(scalaSettings)
  .settings(
    libraryDependencies ++= Seq(
      // Streaming
      Dependencies.Fs2.core,
      Dependencies.Fs2.io,
      // Protocol parsing
      Dependencies.Scodec.core,
      Dependencies.Scodec.bits,
      Dependencies.Scodec.stream,
      // Json
      Dependencies.Circe.core,
      Dependencies.Circe.generic,
      Dependencies.Circe.parser,
      Dependencies.Circe.genericExtras,
      // Logging
      Dependencies.Log4Cats.core,
      Dependencies.Log4Cats.slf4j,
      Dependencies.Logging.logback
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
