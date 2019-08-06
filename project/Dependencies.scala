import sbt._

object Dependencies {
  private lazy val zioVersion              = "1.0.0-RC11-1"
  private lazy val scalatestVersion        = "3.0.8"
  private lazy val betterMonadicForVersion = "0.3.1"
  private lazy val kindProjectorVersion    = "0.10.3"

  lazy val betterMonadicFor = "com.olegpy"    %% "better-monadic-for" % betterMonadicForVersion
  lazy val kindProjector    = "org.typelevel" %% "kind-projector"     % kindProjectorVersion
  lazy val zio              = "dev.zio"       %% "zio"                % zioVersion
  lazy val scalatest        = "org.scalatest" %% "scalatest"          % scalatestVersion
}
