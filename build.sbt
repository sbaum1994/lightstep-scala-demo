scalaVersion := "2.12.6"

name := "scala-example"
organization := "lightstep"
version := "1.0"
fork in run := true
assemblyJarName in assembly := "span-generator.jar"
mainClass in assembly := Some("Main")

assemblyMergeStrategy in assembly := {
  case "META-INF/io.netty.versions.properties" => MergeStrategy.first
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

resolvers += Resolver.bintrayRepo("jvican", "releases")

libraryDependencies += "org.typelevel" %% "cats-core" % "1.1.0"
libraryDependencies += "io.netty" % "netty-tcnative-boringssl-static" % "2.0.12.Final"
libraryDependencies += "io.grpc" % "grpc-netty" % "1.14.0"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
libraryDependencies += "com.github.scopt" %% "scopt" % "3.7.0"
libraryDependencies += "io.circe" %% "circe-yaml" % "0.8.0"

val circeVersion = "0.9.3"

libraryDependencies ++= Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser"
).map(_ % circeVersion)

libraryDependencies ++= Seq(
  ("com.lightstep.tracer" % "tracer-grpc" % "0.15.4").
    exclude("com.google.api.grpc", "googleapis-common-protos")
)

libraryDependencies ++= Seq(
  ("com.lightstep.tracer" % "lightstep-tracer-jre" % "0.14.3").
    exclude("com.google.api.grpc", "googleapis-common-protos")
)