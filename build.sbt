val scala3Version = "3.8.2"
val munitVersion  = "1.0.0"

lazy val commonSettings = Seq(
  scalaVersion := scala3Version,
  organization := "chess",
  scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked"),
)

lazy val model = project
  .in(file("modules/model"))
  .settings(
    commonSettings,
    name := "chess-model",
  )

lazy val rules = project
  .in(file("modules/rules"))
  .settings(
    commonSettings,
    name := "chess-rules",
  )
  .dependsOn(model)

lazy val engine = project
  .in(file("modules/engine"))
  .settings(
    commonSettings,
    name := "chess-engine",
  )
  .dependsOn(model, rules)

lazy val `ui-text` = project
  .in(file("modules/ui-text"))
  .settings(
    commonSettings,
    name := "chess-ui-text",
  )
  .dependsOn(engine)

lazy val tests = project
  .in(file("modules/tests"))
  .settings(
    commonSettings,
    name               := "chess-tests",
    libraryDependencies += "org.scalameta" %% "munit" % munitVersion % Test,
    testFrameworks     += new TestFramework("munit.Framework"),
  )
  .dependsOn(model, rules, engine, `ui-text`)

lazy val root = project
  .in(file("."))
  .aggregate(model, rules, engine, `ui-text`, tests)
  .settings(
    name := "chess",
    // suppress unused warnings for the aggregator root
    publish / skip := true,
  )
