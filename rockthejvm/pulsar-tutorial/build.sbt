val scala3Version = "2.13.6"

val pulsar4sVersion = "2.7.3"

lazy val pulsar4s      = "com.sksamuel.pulsar4s" %% "pulsar4s-core"   % pulsar4sVersion
lazy val pulsar4sCirce = "com.sksamuel.pulsar4s" %% "pulsar4s-circe"  % pulsar4sVersion
lazy val junit         = "com.novocode"           % "junit-interface" % "0.11"

lazy val root = project
  .in(file("."))
  .settings(
    name         := "Pulsar Tutorial",
    version      := "0.1.0-SNAPSHOT",
    scalaVersion := scala3Version,
    libraryDependencies ++= Seq(
      pulsar4s,
      pulsar4sCirce,
      junit % "test"
    )
  )
