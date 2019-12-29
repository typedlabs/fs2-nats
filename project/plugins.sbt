// CI
// addSbtPlugin("ch.epfl.scala" % "sbt-release-early" % "2.1.1")
addSbtPlugin("org.foundweekends" % "sbt-bintray" % "0.5.4")
addSbtPlugin("com.dwijnand" % "sbt-travisci" % "1.2.0")

// Verification/Validation
// addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.6.1")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.9.11")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.3.0")
addSbtPlugin("io.github.davidgregory084" % "sbt-tpolecat" % "0.1.8")

// Documentation
addSbtPlugin("org.scalameta" % "sbt-mdoc" % "2.0.3")
addSbtPlugin("com.47deg" % "sbt-microsites" % "1.0.2")

// Development
addSbtPlugin("io.spray" % "sbt-revolver" % "0.9.1")
addSbtPlugin("ch.epfl.scala" % "sbt-bloop" % "1.3.2")
