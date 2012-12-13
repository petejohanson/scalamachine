resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

resolvers += Resolver.url("scalasbt", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

resolvers += "jgit-repo" at "http://download.eclipse.org/jgit/maven"

libraryDependencies <+= sbtVersion(v => "com.github.siasia" %% "xsbt-web-plugin" % (v+"-0.2.11.1"))

addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.1.0")

addSbtPlugin("com.jsuereth" % "xsbt-gpg-plugin" % "0.6")

addSbtPlugin("com.jsuereth" % "sbt-site-plugin" % "0.4.0")

addSbtPlugin("com.jsuereth" % "sbt-ghpages-plugin" % "0.4.0")

addSbtPlugin("org.ensime" % "ensime-sbt-cmd" % "0.0.10")

addSbtPlugin("net.virtual-void" % "sbt-dependency-graph" % "0.7.0")

