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

// Compute the JavaFX platform classifier at build time
val jfxClassifier: String = {
  val os   = sys.props.getOrElse("os.name", "").toLowerCase
  val arch = sys.props.getOrElse("os.arch", "").toLowerCase
  if      (os.contains("mac") && arch.contains("aarch64")) "mac-aarch64"
  else if (os.contains("mac"))                              "mac"
  else if (os.contains("win"))                             "win"
  else                                                      "linux"
}

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

lazy val bots = (project in file("modules/bots"))
  .dependsOn(engine)
  .settings(moduleSettings("org.maichess.mono.bots"): _*)
  .settings(coverageSettings: _*)
  .settings(name := "maichess-bots")

lazy val uiFx = (project in file("modules/ui-fx"))
  .dependsOn(bots)
  .settings(moduleSettings("org.maichess.mono.uifx"): _*)
  .settings(coverageEnabled   := false)
  .settings(wartremoverErrors  := Nil, wartremoverWarnings := Nil)
  .settings(name := "maichess-ui-fx")
  .settings(run / fork := true)
  .settings(
    libraryDependencies ++= Seq("base", "controls", "graphics").map { m =>
      "org.openjfx" % s"javafx-$m" % "21" classifier jfxClassifier
    }
  )

lazy val uiText = (project in file("modules/ui-text"))
  .dependsOn(bots, uiFx)
  .settings(moduleSettings("org.maichess.mono.ui"): _*)
  .settings(coverageEnabled := false)
  .settings(name := "maichess-ui-text")
  .settings(run / fork := true)
  .settings(run / connectInput := true)
  .settings(run / outputStrategy := Some(StdoutOutput))
  .settings(
    libraryDependencies ++= Seq(
      "com.googlecode.lanterna" % "lanterna" % "3.1.2"
    )
  )
  .settings(
    assembly / mainClass       := Some("org.maichess.mono.ui.runGame"),
    assembly / assemblyJarName := "maichess.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("module-info.class")                       => MergeStrategy.discard
      case PathList("META-INF", "versions", _ @ _*)            => MergeStrategy.first
      case PathList("META-INF", "substrate", "config", _ @_*) => MergeStrategy.first
      case x => (assembly / assemblyMergeStrategy).value(x)
    }
  )

lazy val tests = (project in file("modules/tests"))
  .dependsOn(model, rules, engine, bots, uiText)
  .settings(moduleSettings("org.maichess.mono.tests"): _*)
  .settings(
    name := "maichess-tests",
    libraryDependencies ++= Seq(
      "org.scalameta" %% "munit" % "1.0.4" % Test
    ),
    testFrameworks += new TestFramework("munit.Framework"),
    Test / classLoaderLayeringStrategy := ClassLoaderLayeringStrategy.Flat,
    Test / wartremoverErrors   := Nil,
    Test / wartremoverWarnings := Nil,
    Test / scalacOptions ~= { opts =>
      opts.filterNot(o => o.startsWith("-P:wartremover:") || o.startsWith("-Xplugin"))
    }
  )

lazy val root = (project in file("."))
  .aggregate(model, rules, engine, bots, uiFx, uiText, tests)
  .settings(
    name := "maichess-mono",
    Compile / unmanagedSourceDirectories := Nil,
    Test    / unmanagedSourceDirectories := Nil
  )
