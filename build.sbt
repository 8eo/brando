name         := "brando"
organization := "com.digital-achiever"
scalaVersion := "2.13.8"
scalacOptions ++= Seq("-unchecked", "-deprecation", "-feature", "-Ywarn-unused")
semanticdbEnabled in ThisBuild := true
semanticdbVersion in ThisBuild := scalafixSemanticdb.revision

publishTo := Some("Horn SBT" at "https://maven.pkg.github.com/8eo/brando")

val akkaV = "2.6.19"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor"   % akkaV,
  "org.scalatest"     %% "scalatest"    % "3.0.9" % "test",
  "com.typesafe.akka" %% "akka-testkit" % akkaV   % "test"
)

parallelExecution in Test := false
