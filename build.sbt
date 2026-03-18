ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.2"
ThisBuild / wartremoverErrors ++= Warts.unsafe

def moduleSettings(pkg: String): Seq[Def.Setting[?]] = Seq(
  idePackagePrefix.withRank(KeyRanks.Invisible) := Some(pkg)
)

val coverageSettings: Seq[Def.Setting[?]] = Seq(
  coverageMinimumStmtTotal := 100,
  coverageFailOnMinimum    := true
)

lazy val model = (project in file("modules/model"))
  .settings(moduleSettings("org.maichess.mono.model"): _*)
  .settings(coverageSettings: _*)
  .settings(name := "maichess-model")

lazy val rules = (project in file("modules/rules"))
  .dependsOn(model)
  .settings(moduleSettings("org.maichess.mono.rules"): _*)
  .settings(coverageSettings: _*)
  .settings(name := "maichess-rules")

lazy val engine = (project in file("modules/engine"))
  .dependsOn(rules)
  .settings(moduleSettings("org.maichess.mono.engine"): _*)
  .settings(coverageSettings: _*)
  .settings(name := "maichess-engine")

lazy val uiText = (project in file("modules/ui-text"))
  .dependsOn(engine)
  .settings(moduleSettings("org.maichess.mono.ui"): _*)
  .settings(coverageEnabled := false)
  .settings(name := "maichess-ui-text")
  .settings(
    libraryDependencies ++= Seq(
      "org.jline" % "jline-terminal" % "3.29.0",
      "org.jline" % "jline-reader"   % "3.29.0"
    )
  )

lazy val tests = (project in file("modules/tests"))
  .dependsOn(model, rules, engine, uiText)
  .settings(moduleSettings("org.maichess.mono.tests"): _*)
  .settings(
    name := "maichess-tests",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.4" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    Test / wartremoverErrors   := Nil,
    Test / wartremoverWarnings := Nil,
    Test / scalacOptions ~= { opts =>
      opts.filterNot(o => o.startsWith("-P:wartremover:") || o.startsWith("-Xplugin"))
    }
  )

lazy val root = (project in file("."))
  .aggregate(model, rules, engine, uiText, tests)
  .settings(
    name := "maichess-mono",
    Compile / unmanagedSourceDirectories := Nil,
    Test    / unmanagedSourceDirectories := Nil
  )
