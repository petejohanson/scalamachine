import sbt._
import sbtrelease._
import ReleasePlugin._
import org.scalastyle.sbt.ScalastylePlugin
import com.earldouglas.xwp._
import XwpPlugin._
import com.typesafe.sbt.SbtSite._
import com.typesafe.sbt.SbtGit._
import com.typesafe.sbt.SbtGhPages.{ ghpages, GhPagesKeys => ghkeys }
import Keys._

object BuildSettings {
  val org = "com.stackmob"

  lazy val publishSetting = publishTo <<= version { v: String =>
    val nexus = "https://oss.sonatype.org/"
    if (v.trim.endsWith("SNAPSHOT")) {
      Some("snapshots" at nexus + "content/repositories/snapshots")
    } else {
      Some("releases" at nexus + "service/local/staging/deploy/maven2")
    }
  }

  lazy val publishSettings = Seq(
    publishSetting,
    publishMavenStyle := true,
    pomIncludeRepository := { x => false },
    pomExtra := (
      <url>https://github.com/stackmob/scalamachine</url>
      <licenses>
        <license>
          <name>Apache 2</name>
          <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          <distribution>repo</distribution>
        </license>
      </licenses>
      <scm>
        <url>git@github.com:stackmob/scalamachine.git</url>
        <connection>scm:git:git@github.com:stackmob/scalamachine.git</connection>
      </scm>
      <developers>
        <developer>
          <id>jrwest</id>
          <name>Jordan West</name>
          <url>http://github.com/jrwest</url>
        </developer>
        <developer>
          <id>taylorleese</id>
          <name>Taylor Leese</name>
          <url>http://github.com/taylorleese</url>
        </developer>
        <developer>
          <id>ayakushev99</id>
          <name>Alex Yakushev</name>
          <url>http://github.com/ayakushev99</url>
        </developer>
        <developer>
          <id>arschles</id>
          <name>Aaron Schlesinger</name>
          <url>http://github.com/arschles</url>
        </developer>
      </developers>
    )
  )

  val standardSettings = Defaults.defaultSettings ++ ScalastylePlugin.projectSettings ++ releaseSettings ++ Seq(
    organization := org,
    scalaVersion := "2.10.2",
    scalacOptions := Seq("-deprecation", "-unchecked", "-feature", "-language:implicitConversions,", "-language:higherKinds"),
    shellPrompt <<= ShellPrompt.prompt,
    javaOptions in test ++= Seq("-Xmx2048m"),
    testOptions in Test += Tests.Argument("html", "console"),
    publishArtifact in Test := false,
    resolvers += ("twitter repository" at "http://maven.twttr.com"),
    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"
  )

}

object Dependencies {

  val scalazVsn = "7.0.0"

  lazy val scalazCore      = "org.scalaz"              %% "scalaz-core"                  % scalazVsn         % "compile"
  lazy val scalazIteratee  = "org.scalaz"              %% "scalaz-iteratee"              % scalazVsn         % "compile"
  lazy val scalazEffect    = "org.scalaz"              %% "scalaz-effect"                % scalazVsn         % "compile"
  lazy val slf4j           = "org.slf4j"               % "slf4j-api"                     % "1.7.5"           % "compile"
  lazy val commonsHttp     = "commons-httpclient"      % "commons-httpclient"            % "3.1"             % "compile"
  lazy val liftweb         = "net.liftweb"             %% "lift-webkit"                  % "2.5"             % "compile"
  lazy val servletApi      = "javax.servlet"           % "servlet-api"                   % "2.5"             % "compile"
  lazy val finagle         = "com.twitter"             %% "finagle-http"                 % "6.5.0"           % "compile"
  lazy val logback         = "ch.qos.logback"          % "logback-classic"               % "1.0.13"          % "compile"

  lazy val specs2          = "org.specs2"              %% "specs2"                       % "1.14"            % "test"
  lazy val scalacheck      = "org.scalacheck"          %% "scalacheck"                   % "1.10.1"          % "test"
  lazy val mockito         = "org.mockito"             % "mockito-all"                   % "1.9.0"           % "test"
  lazy val hamcrest        = "org.hamcrest"            % "hamcrest-all"                  % "1.1"             % "test"
  lazy val pegdown         = "org.pegdown"             % "pegdown"                       % "1.0.2"           % "test"

}

object ScalamachineBuild extends Build {
  import BuildSettings._
  import Dependencies._

  private def updatedRepo(repo: SettingKey[File], remote: SettingKey[String], branch: SettingKey[Option[String]]) = {
    (repo, remote, branch, GitKeys.gitRunner, streams) map { (local, uri, branch, git, s) =>
      git.updated(remote = uri, cwd = local, branch = branch, log = s.log)
      local
    }
  }

  val docsRepo = SettingKey[String]("docs-repo", "the remote repo that contains documentation for this project")

  lazy val scalamachine = Project("scalamachine", file("."),
    settings = standardSettings ++ publishSettings ++ Seq(publishArtifact in Compile := false),
    aggregate = Seq(core, scalaz7utils, servlet, lift, netty)
  )

  lazy val core = Project("scalamachine-core", file("core"),
    settings = standardSettings ++ publishSettings ++ site.settings ++ site.jekyllSupport("jekyll") ++ site.includeScaladoc() ++ ghpages.settings ++
      Seq(
        name := "scalamachine-core",
        libraryDependencies ++= Seq(scalazCore, scalazIteratee, scalazEffect, slf4j, commonsHttp, specs2, scalacheck, mockito, hamcrest, pegdown),
        git.remoteRepo := "git@github.com:stackmob/scalamachine",
        docsRepo := "git@github.com:stackmob/scalamachine.site",
        git.branch in ghkeys.updatedRepository := Some("master"),
        ghkeys.updatedRepository <<= updatedRepo(ghkeys.repository, docsRepo, git.branch in ghkeys.updatedRepository)
      )
  )

  lazy val scalaz7utils = Project("scalamachine-scalaz7", file("scalaz7"),
    dependencies = Seq(core),
    settings = standardSettings ++ publishSettings ++
      Seq(
        name := "scalamachine-scalaz7",
        libraryDependencies ++= Seq(scalazCore)
      )
  )

  lazy val servlet = Project("scalamachine-servlet", file("servlet"),
    dependencies = Seq(core), 
    settings = standardSettings ++ publishSettings ++
      Seq(
        name := "scalamachine-servlet",
        libraryDependencies ++= Seq(servletApi)
      )
  )
  
  lazy val lift = Project("scalamachine-lift", file("lift"),
    dependencies = Seq(core), 
    settings = standardSettings ++ publishSettings ++
      Seq(
        name := "scalamachine-lift",
        libraryDependencies ++= Seq(liftweb)
      )
  )

  lazy val netty = Project("scalamachine-netty", file("netty"),
    dependencies = Seq(core),
    settings = standardSettings ++ publishSettings ++
      Seq(
        name := "scalamachine-netty",
        libraryDependencies ++= Seq(finagle)
      )
  )

  lazy val servletExample = Project("scalamachine-servlet-example", file("examples/servlet"),
    dependencies = Seq(servlet),
    settings = standardSettings ++ jetty() ++ warSettings ++ webappSettings ++
      Seq(
        name := "scalamachine-servlet-example",
        libraryDependencies ++= Seq(logback)
      )
  )

  lazy val liftExample = Project("scalamachine-lift-example", file("examples/lift"),
    dependencies = Seq(lift),
    settings = standardSettings ++ jetty() ++ warSettings ++ webappSettings ++
      Seq(
        name := "scalamachine-lift-example",
        libraryDependencies ++= Seq(logback)
      )
  )

  lazy val finagleExample = Project("scalamachine-finagle-example", file("examples/finagle"),
    dependencies = Seq(netty),
    settings = standardSettings ++
      Seq(
        name := "scalamachine-finagle-example",
        libraryDependencies ++= Seq(logback)
      )
  )

  lazy val nettyExample = Project("scalamachine-netty-example", file("examples/netty"), 
    dependencies = Seq(netty),
    settings = standardSettings ++
      Seq(
        name := "scalamachine-netty-example",
        libraryDependencies ++= Seq(logback)
      )
  )

}

object ShellPrompt {
  val prompt = name(name => { state: State =>
    object devnull extends ProcessLogger {
      override def info(s: => String) { }
      override def error(s: => String) { }
      override def buffer[T](f: => T): T = f
    }
    val current = """\*\s+(\w+)""".r
    def gitBranches = ("git branch --no-color" lines_! devnull mkString)
    "%s | %s> " format (
      name,
      current findFirstMatchIn gitBranches map (_.group(1)) getOrElse "-"
    )
  })
}
