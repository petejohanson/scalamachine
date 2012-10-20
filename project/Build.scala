import sbt._
import Keys._
import com.typesafe.sbt.SbtSite._
import SiteKeys._
import com.github.siasia._
import WebPlugin._


object BuildSettings {

  val org = "com.stackmob"
  val vsn = "0.2.0-SNAPSHOT"
  val scalaVsn = "2.10.0-M7"

  lazy val publishSetting = publishTo <<= version { v: String =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }


  lazy val publishSettings = Seq(
    publishSetting,
    publishMavenStyle := true,
    pomIncludeRepository := { x => false },
    pomExtra := (
      <url>https://github.com/jrwest/scalamachine/wiki</url>
        <licenses>
          <license>
            <name>Apache 2</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
            <distribution>repo</distribution>
          </license>
        </licenses>
        <scm>
          <url>git@github.com:jrwest/scalamachine.git</url>
          <connection>scm:git:git@github.com:jrwest/scalamachine.git</connection>
        </scm>
        <developers>
          <developer>
            <id>jrwest</id>
            <name>Jordan West</name>
            <url>http://github.com/jrwest</url>
          </developer>
        </developers>
      )
  )

  val standardSettings = Defaults.defaultSettings ++ Seq(
    organization := org,
    version := vsn,
    scalaVersion := scalaVsn,
    resolvers += ("twitter repository" at "http://maven.twttr.com"),
    shellPrompt <<= ShellPrompt.prompt,
    scalacOptions ++= Seq("-deprecation", "-unchecked", "-feature", "-language:implicitConversions", "-language:higherKinds"),
    testOptions in Test += Tests.Argument("html console"),
    publishArtifact in Test := false,
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )

}

object Dependencies {
  /* Core Dependencies */
  // FUCK YOU SBT!!! This should use %% but sbt 0.12 got all proper about binary compat.
  lazy val scalaz7        = "org.scalaz"              % "scalaz-iteratee_2.10.0-M7"     % "7.0.0-M3"        % "compile"
  // Don't want to keep this dependency long term but for now its fastest way to get date parsing for http
  lazy val commonsHttp    = "commons-httpclient"      % "commons-httpclient"            % "3.1"             % "compile"

  /* Host Framework Dependencies */
  lazy val netty          = "io.netty"                % "netty"                         % "3.5.7.Final"     % "compile"
  lazy val servletApi     = "javax.servlet"           % "servlet-api"                   % "2.5"             % "compile"

  /* Example Project Dependencies */
  lazy val jetty          = "org.eclipse.jetty"       % "jetty-webapp"                  % "7.3.0.v20110203" % "container"

  /* Logging */
  lazy val logback        = "ch.qos.logback"          % "logback-classic"               % "1.0.0"           % "compile"
  lazy val slf4j          = "org.slf4j"               % "slf4j-api"                     % "1.6.4"           % "compile"

  /* Test */
  // ONCE AGAIN< FUCK YOU SBT, there is probably a new specs version for this but im not searching right now
  lazy val specs2         = "org.specs2"              %  "specs2_2.10.0-M7"             % "1.12.1.1"        % "test"
  // AND YES, FUCK YOU SBT...AGAIN
  lazy val scalacheck     = "org.scalacheck"          % "scalacheck_2.10.0-M7"          % "1.10.0"          % "test"
  lazy val mockito        = "org.mockito"             % "mockito-all"                   % "1.9.0"           % "test"
  lazy val hamcrest       = "org.hamcrest"            % "hamcrest-all"                  % "1.1"             % "test"
  lazy val pegdown        = "org.pegdown"             % "pegdown"                       % "1.0.2"           % "test"
}

object ScalamachineBuild extends Build {
  import BuildSettings._
  import Dependencies._

  lazy val scalamachine = Project("scalamachine", file("."),
    settings = standardSettings ++ publishSettings ++ Seq(publishArtifact in Compile := false),
    aggregate = Seq(core,nettySupport,servletSupport,nettyExample,servletExample)
  )

  lazy val core = Project("scalamachine-core", file("core"),
    settings = standardSettings ++ publishSettings ++ site.settings ++ site.jekyllSupport("jekyll") ++ site.includeScaladoc() ++ 
      Seq(
        name := "scalamachine-core",
        libraryDependencies ++= Seq(scalaz7,commonsHttp,specs2,scalacheck,mockito,hamcrest,pegdown)
      )
  )

  lazy val nettySupport = Project("scalamachine-netty", file("netty"),
    dependencies = Seq(core),
    settings = standardSettings ++ publishSettings ++
      Seq(
        name := "scalamachine-netty",
        libraryDependencies ++= Seq(netty, slf4j)
      )
  )


  lazy val servletSupport = Project("scalamachine-servlet", file("servlet"),
    dependencies = Seq(core), 
    settings = standardSettings ++ publishSettings ++
      Seq(
        name := "scalamachine-servlet",
        libraryDependencies ++= Seq(servletApi)
      )
  )
  
  lazy val nettyExample = Project("netty-example", file("examples/netty"), 
    dependencies = Seq(nettySupport),
    settings = standardSettings ++
      Seq(
	name := "scalamachine-netty-example", 
        libraryDependencies ++= Seq(logback)
    )
  )              

  lazy val servletExample = Project("servlet-example", file("examples/servlet"),
    dependencies = Seq(servletSupport),
    settings = standardSettings ++ webSettings ++
      Seq(
        name := "scalamachine-servlet-example",
        libraryDependencies ++= Seq(jetty,logback)
      )
  )

}

object ShellPrompt {
  val prompt = name(name => { state: State =>
    object devnull extends ProcessLogger {
      def info(s: => String) {}
      def error(s: => String) { }
      def buffer[T](f: => T): T = f
    }
    val current = """\*\s+(\w+)""".r
    def gitBranches = ("git branch --no-color" lines_! devnull mkString)
    "%s | %s> " format (
      name,
      current findFirstMatchIn gitBranches map (_.group(1)) getOrElse "-"
      )
  })
}

