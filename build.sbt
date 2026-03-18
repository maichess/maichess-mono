ThisBuild / version      := "0.1.0-SNAPSHOT"
ThisBuild / scalaVersion := "3.8.2"
ThisBuild / wartremoverErrors ++= Warts.unsafe

def moduleSettings(pkg: String): Seq[Def.Setting[?]] = Seq(
  idePackagePrefix.withRank(KeyRanks.Invisible) := Some(pkg)
)

lazy val model = (project in file("modules/model"))
  .settings(moduleSettings("org.maichess.mono.model"): _*)
  .settings(name := "maichess-model")

lazy val rules = (project in file("modules/rules"))
  .dependsOn(model)
  .settings(moduleSettings("org.maichess.mono.rules"): _*)
  .settings(name := "maichess-rules")

lazy val engine = (project in file("modules/engine"))
  .dependsOn(rules)
  .settings(moduleSettings("org.maichess.mono.engine"): _*)
  .settings(name := "maichess-engine")

lazy val uiText = (project in file("modules/ui-text"))
  .dependsOn(engine)
  .settings(moduleSettings("org.maichess.mono.ui"): _*)
  .settings(name := "maichess-ui-text")

lazy val tests = (project in file("modules/tests"))
  .dependsOn(model, rules, engine, uiText)
  .settings(moduleSettings("org.maichess.mono.tests"): _*)
  .settings(
    name := "maichess-tests",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.4" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework")
  )

lazy val root = (project in file("."))
  .aggregate(model, rules, engine, uiText, tests)
  .settings(
    name := "maichess-mono",
    Compile / unmanagedSourceDirectories := Nil,
    Test    / unmanagedSourceDirectories := Nil
  )
