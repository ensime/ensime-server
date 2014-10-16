import sbt._
import java.io._
import java.util.concurrent.atomic.AtomicReference
import net.virtualvoid.sbt.graph.Plugin.graphSettings

// NOTE: the following skips the slower tests
// test-only * -- -l SlowTest

organization := "org.ensime"

name := "ensime"

// we also create a 2.9.3 build in travis
scalaVersion := "2.9.2"

version := "0.9.10-SNAPSHOT"

resolvers += "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/"

resolvers += "Akka Repo" at "http://repo.akka.io/repository"

resolvers += Resolver.sonatypeRepo("snapshots")

libraryDependencies ++= Seq(
  "com.github.stacycurl"       %% "pimpathon-core"       % "1.0.0",
  // https://github.com/sirthias/parboiled/issues/79
  "org.parboiled"              %% "parboiled-scala"      % {if (scalaVersion.value == "2.9.2") "1.1.4" else "1.1.6"},
  "com.h2database"             %  "h2"                   % "1.4.181",
  // https://github.com/slick/slick/issues/991
  //"org.scalaquery"             %% "scalaquery"           % "0.9.5",
  "org.scalaquery"             %  "scalaquery_2.9.0"     % "0.9.4",
  "com.jolbox"                 %  "bonecp"               % "0.8.0.RELEASE",
  "org.apache.commons"         %  "commons-vfs2"         % "2.0" intransitive(),
  // lucene 4.8+ needs Java 7: http://www.gossamer-threads.com/lists/lucene/general/225300
  "org.apache.lucene"          %  "lucene-core"          % "4.7.2",
  "org.apache.lucene"          %  "lucene-analyzers-common" % "4.7.2",
  "org.ow2.asm"                %  "asm-commons"          % "5.0.3",
  "org.ow2.asm"                %  "asm-util"             % "5.0.3",
  "com.danieltrinh"            %% "scalariform"          % "0.1.5",
  "org.scala-lang"             %  "scala-compiler"       % scalaVersion.value,
  "org.scala-lang"             %  "scalap"               % scalaVersion.value,
  "com.typesafe.akka"          %  "akka-actor"           % "2.0.5",
  "com.typesafe.akka"          %  "akka-slf4j"           % "2.0.5",
  "com.typesafe.akka"          %  "akka-testkit"         % "2.0.5" % "test",
  "commons-io"                 %  "commons-io"           % "2.4"   % "test",
  "org.scalatest"              %% "scalatest"            % "1.9.2" % "test",
//  "org.scalamock"              %% "scalamock-scalatest-support" % "2.4" % "test",
  "ch.qos.logback"             %  "logback-classic"      % "1.1.2",
  "org.slf4j"                  %  "jul-to-slf4j"         % "1.7.7",
  "org.slf4j"                  %  "jcl-over-slf4j"       % "1.7.7",
  "org.scala-refactoring"      %% "org.scala-refactoring.library" % "0.6.2"
)

// epic hack to get the tools.jar JDK dependency
val JavaTools = List[Option[String]] (
  // manual
  sys.env.get("JDK_HOME"),
  sys.env.get("JAVA_HOME"),
  // osx
  try Some("/usr/libexec/java_home".!!.trim)
  catch {
    case _: Throwable => None
  },
  // fallback
  sys.props.get("java.home").map(new File(_).getParent),
  sys.props.get("java.home")
).flatten.map { n =>
  new File(n + "/lib/tools.jar")
}.find(_.exists).getOrElse (
  throw new FileNotFoundException (
    """Could not automatically find the JDK/lib/tools.jar.
      |You must explicitly set JDK_HOME or JAVA_HOME.""".stripMargin
  )
)

internalDependencyClasspath in Compile += { Attributed.blank(JavaTools) }

scalacOptions in Compile ++= Seq(
  "-encoding", "UTF-8", "-unchecked", "-deprecation", "-Xfatal-warnings"
)

javacOptions in (Compile, compile) ++= Seq (
  "-source", "1.6", "-target", "1.6", "-Xlint:all", "-Werror",
  "-Xlint:-options", "-Xlint:-path", "-Xlint:-processing"
)

javacOptions in doc ++= Seq("-source", "1.6")

maxErrors := 1

fork := true

// FIXME: https://github.com/paulbutcher/scalamock-sbt-plugin/issues/2
// following the advice in http://paulbutcher.com/2011/11/06/scalamock-step-by-step/
// autoCompilerPlugins := true
// addCompilerPlugin("org.scalamock" %% "scalamock-compiler-plugin" % "2.4")
// ScalaMockPlugin.generateMocksSettings

//tests should be isolated, but let's keep an eye on stability
//parallelExecution in Test := false

// passes locations of example jars to the tests
def jars(cp: Classpath): String = {
  for {
    att <- cp
    file = att.data
    if file.isFile & file.getName.endsWith(".jar")
  } yield file.getAbsolutePath
}.mkString(",")

// passes the location of ENSIME's class dirs to the tests
def classDirs(cp: Classpath): String = {
  for {
    att <- cp
    file = att.data
    if file.isDirectory
  } yield file.getAbsolutePath
}.mkString(",")

javaOptions ++= Seq("-XX:MaxPermSize=128m", "-Xmx1g", "-XX:+UseConcMarkSweepGC")

javaOptions in Test ++= infoForTests.value

javaOptions in Test += "-Dakka.actor.debug.receive=on"

lazy val infoForTests = TaskKey[Seq[String]]("infoForTests", "for use in tests")

// TODO: cache the results
// http://stackoverflow.com/questions/25410484
infoForTests := Seq(
  "-Densime.compile.jars=" + jars((fullClasspath in Compile).value),
  "-Densime.test.jars=" + jars((fullClasspath in Test).value),
  "-Densime.compile.classDirs=" + classDirs((fullClasspath in Compile).value),
  "-Densime.test.classDirs=" + classDirs((fullClasspath in Test).value),
  "-Dscala.version=" + scalaVersion.value,
  // sorry! this puts a source/javadoc dependency on running our tests
  "-Densime.jars.sources=" + (updateClassifiers in Test).value.select(
    artifact = artifactFilter(classifier = "sources")
  ).mkString(",")
)

// adds our example projects to the test compile
unmanagedSourceDirectories in Test += baseDirectory.value / "src/example-simple"

// full stacktraces in scalatest
testOptions in Test += Tests.Argument("-oF")

graphSettings

scalariformSettings

licenses := Seq("BSD 3 Clause" -> url("http://opensource.org/licenses/BSD-3-Clause"))

homepage := Some(url("http://github.com/ensime/ensime-server"))

publishTo <<= version { v: String =>
  val nexus = "https://oss.sonatype.org/"
  if (v.contains("SNAP")) Some("snapshots" at nexus + "content/repositories/snapshots")
  else                    Some("releases"  at nexus + "service/local/staging/deploy/maven2")
}

credentials += Credentials(
  "Sonatype Nexus Repository Manager", "oss.sonatype.org",
  sys.env.get("SONATYPE_USERNAME").getOrElse(""),
  sys.env.get("SONATYPE_PASSWORD").getOrElse("")
)
