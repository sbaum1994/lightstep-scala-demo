scalaVersion := "2.12.6"

name := "scala-example"
organization := "lightstep"
version := "1.0"

libraryDependencies += "org.typelevel" %% "cats-core" % "1.1.0"
libraryDependencies += "io.netty" % "netty-tcnative-boringssl-static" % "2.0.12.Final"
libraryDependencies += "io.grpc" % "grpc-netty" % "1.14.0"
libraryDependencies += "com.lightstep.tracer" % "lightstep-tracer-jre" % "0.14.3"
libraryDependencies += "com.lightstep.tracer" % "tracer-grpc" % "0.15.4"
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3"
