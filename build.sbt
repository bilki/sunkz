import Dependencies._

name := "sunkz"

version := "0.1"

scalaVersion := "2.12.9"

libraryDependencies ++= Seq(zio, scalatest)

addCompilerPlugin(betterMonadicFor)
addCompilerPlugin(kindProjector)
