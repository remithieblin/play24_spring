name := """play24"""

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  jdbc,
  cache,
  ws,
  "org.springframework" % "spring-context" % "4.2.1.RELEASE",
  "com.actimust"% "play-spring-loader" % "1.0-SNAPSHOT",
  specs2 % Test
)

resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"

// Play provides two styles of routers, one expects its actions to be injected, the
// other, legacy style, accesses its actions statically.
routesGenerator := InjectedRoutesGenerator

unmanagedSourceDirectories in Compile += baseDirectory.value / "app"
scalaSource in Compile := baseDirectory.value / "src/main/scala"
javaSource in Compile := baseDirectory.value / "src/main/java"

javaOptions in Test += "-Dconfig.file=test/conf/application.conf"