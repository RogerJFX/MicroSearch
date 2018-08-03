name := "soracle"

version := "0.1"

scalaVersion := "2.12.6"

libraryDependencies ++= Seq (
  "org.apache.lucene" % "lucene-core" % "7.4.0",
  "org.apache.lucene" % "lucene-analyzers-common" % "7.4.0",
  "org.apache.lucene" % "lucene-queryparser" % "7.4.0",
  "org.apache.lucene" % "lucene-analyzers-phonetic" % "7.4.0",
  "commons-codec" % "commons-codec" % "1.11",

 // "com.chuusai" %% "shapeless" % "2.3.3",

  "com.typesafe.play" %% "play-netty-server" % "2.6.13",
  "com.typesafe.play" %% "play-ahc-ws-standalone" % "1.1.7",

  "com.typesafe.scala-logging" % "scala-logging-slf4j_2.11" % "2.1.2",
  "ch.qos.logback" % "logback-classic" % "1.1.7",

  "org.scalatest" %% "scalatest" % "3.0.1" % Test,
  "org.scalamock" %% "scalamock" % "4.1.0" % Test



)