import sbt.librarymanagement.CrossVersion

object CompilerSettings {

  // graciously reused from https://github.com/softwaremill/sbt-softwaremille

  private val commonScalacOptions = Seq(
    "-deprecation", // Emit warning and location for usages of deprecated APIs.
    "-encoding",
    "UTF-8", // Specify character encoding used by source files.
    "-explaintypes", // Explain type errors in more detail.
    "-feature", // Emit warning and location for usages of features that should be imported explicitly.
    "-language:existentials", // Existential types (besides wildcard types) can be written and inferred
    "-language:higherKinds", // Allow higher-kinded types
    "-language:experimental.macros", // Allow macro definition (besides implementation and application)
    "-language:implicitConversions", // Allow definition of implicit functions called views
    "-language:postfixOps", // Allow postfix operators
    "-unchecked", // Enable additional warnings where generated code depends on assumptions.
    "-Xcheckinit", // Wrap field accessors to throw an exception on uninitialized access.
    "-Ywarn-dead-code", // Warn when dead code is identified.
    "-Ywarn-numeric-widen", // Warn when numerics are widened.
    "-Ywarn-value-discard" // Warn when non-Unit expression results are unused.
  )

  private val scalacOptionsLte212 = Seq(
    "-Xfuture", // Turn on future language features.
    "-Xlint:by-name-right-associative", // By-name parameter of right associative operator.
    "-Xlint:unsound-match", // Pattern match may not be typesafe.
    "-Yno-adapted-args", // Do not adapt an argument list (either by inserting () or creating a tuple) to match the receiver.
    "-Ypartial-unification", // Improves type constructor inference with support for partial unification (SI-2712)
    "-Ywarn-inaccessible", // Warn about inaccessible types in method signatures.
    "-Ywarn-infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Ywarn-nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Ywarn-nullary-unit" // Warn when nullary methods return Unit.
  )

  private val scalacOptionsGte211 = Seq(
    "-Xlint:adapted-args", // Warn if an argument list is modified to match the receiver.
    "-Xlint:delayedinit-select", // Selecting member of DelayedInit.
    "-Xlint:doc-detached", // A Scaladoc comment appears to be detached from its element.
    "-Xlint:inaccessible", // Warn about inaccessible types in method signatures.
    "-Xlint:infer-any", // Warn when a type argument is inferred to be `Any`.
    "-Xlint:missing-interpolator", // A string literal appears to be missing an interpolator id.
    "-Xlint:nullary-override", // Warn when non-nullary `def f()' overrides nullary `def f'.
    "-Xlint:nullary-unit", // Warn when nullary methods return Unit.
    "-Xlint:option-implicit", // Option.apply used implicit view.
    "-Xlint:package-object-classes", // Class or object defined in package object.
    "-Xlint:poly-implicit-overload", // Parameterized overloaded implicit methods are not visible as view bounds.
    "-Xlint:private-shadow", // A private field (or class parameter) shadows a superclass field.
    "-Xlint:stars-align", // Pattern sequence wildcard must align with sequence component.
    "-Xlint:type-parameter-shadow" // A local type parameter shadows a type already in scope.
  )

  private val scalacOptionsGte212 = Seq(
    "-Xlint:constant", // Evaluation of a constant arithmetic expression results in an error.
    "-Ywarn-unused:implicits", // Warn if an implicit parameter is unused.
    "-Ywarn-unused:imports", // Warn if an import selector is not referenced.
    "-Ywarn-unused:locals", // Warn if a local definition is unused.
    "-Ywarn-unused:params", // Warn if a value parameter is unused.
    "-Ywarn-unused:patvars", // Warn if a variable bound in a pattern is unused.
    "-Ywarn-unused:privates", // Warn if a private member is unused.
    "-Ywarn-extra-implicit",
    "-Xfatal-warnings", // Warn when more than one implicit parameter section is defined.
    "-Ymacro-annotations" // Enable macro anotations
  )

  private val scalacOptionsEq211 = List(
    "-Ywarn-unused-import" // Warn if an import selector is not referenced.
  )

  private val scalacOptionsEq210 = List(
    "-Xlint"
  )

  val filterConsoleScalacOptions: Seq[String] => Seq[String] = { options: Seq[String] =>
    options.filterNot(
      Set(
        "-Ywarn-unused:imports",
        "-Ywarn-unused-import",
        "-Ywarn-dead-code",
        "-Xfatal-warnings"
      )
    )
  }

  def scalacOptionsFor(version: String): Seq[String] =
    commonScalacOptions ++ (CrossVersion.partialVersion(version) match {
      case Some((2, min)) if min >= 13 => scalacOptionsGte212 ++ scalacOptionsGte211
      case Some((2, 12))               => scalacOptionsGte212 ++ scalacOptionsGte211 ++ scalacOptionsLte212
      case Some((2, 11))               => scalacOptionsGte211 ++ scalacOptionsEq211 ++ scalacOptionsLte212
      case _                           => scalacOptionsEq210 ++ scalacOptionsLte212
    })

}
