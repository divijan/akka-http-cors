organization  := "com.example"

version       := "0.1"

scalaVersion  := "2.11.5"

scalacOptions := Seq("-unchecked", "-deprecation", "-encoding", "utf8")

libraryDependencies ++= {
  val akkaV = "2.3.9"
  val akkaExperimentalV = "1.0-M3"
  val sprayV = "1.3.2"
  Seq(
    "com.typesafe.akka" %% "akka-http-experimental" % akkaExperimentalV,
    "com.typesafe.akka" %% "akka-stream-experimental" % akkaExperimentalV,
    "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaExperimentalV,
    "com.typesafe.akka" %% "akka-http-xml-experimental" % akkaExperimentalV,
    "org.scala-lang.modules" %% "scala-xml" % "1.0.2",
    "io.spray"            %%  "spray-testkit" % sprayV  % "test",
    "com.typesafe.akka"   %%  "akka-actor"    % akkaV,
    "com.typesafe.akka"   %%  "akka-testkit"  % akkaV   % "test",
    "org.specs2"          %%  "specs2-core"   % "2.3.11" % "test",
    "org.slf4j"           %   "slf4j-api"     % "1.7.7",
    "ch.qos.logback"      % "logback-classic" % "1.1.2",
    "org.clapper"         %% "grizzled-slf4j" % "1.0.2"
  )
}

Revolver.settings
