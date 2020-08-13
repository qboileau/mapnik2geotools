
name := "mn2gt"

version := "0.1"

organization := "org.opengeo"

scalaVersion := "2.13.3"

scalacOptions ++= Seq(
  "-encoding", "utf8", // Option and arguments on same line
//  "-Xfatal-warnings",  // New lines for each options
  "-language:postfixOps"
)

fork in run := true

mainClass in (Compile, run) := Some("me.winslow.d.mn2gt.GUI")

mainClass in assembly := Some("me.winslow.d.mn2gt.GUI")

//resolvers += ScalaToolsSnapshots
resolvers += "ScalaTools snapshots at Sonatype" at "https://oss.sonatype.org/content/repositories/snapshots/"

libraryDependencies ++= Seq(
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0",
  "org.scala-lang.modules" %% "scala-swing" % "2.1.1",
  "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.2",
  "commons-httpclient" % "commons-httpclient" % "3.1",
  "org.specs2" %% "specs2-core" % "4.10.0" % "test"
)

