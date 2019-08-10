package com.lambdarat

import zio.ZIO.succeed
import zio.console._
import zio.{App, RIO, URIO}

import com.lambdarat.ui.WindowSystem
import com.lambdarat.ui.WindowSystem.{WindowHeight, WindowWidth}
import com.lambdarat.ui.ZWindowSystem._

object Main extends App {

  sealed abstract class GlfwError(val msg: String) extends Exception(msg)
  object GlfwError {
    final case object GlfwInitError           extends GlfwError("GLFW Initialization error")
    final case object GlfwWindowCreationError extends GlfwError("GLFW Window creation error")
  }

  override def run(args: List[String]): URIO[Main.Environment, Int] =
    main
      .provide(WindowSystem.Live)
      .foldM(
        err => putStrLn(s"[SUNKZ] Failure with error [${err.getMessage}]") *> succeed(1),
        succeed
      )

  def main: RIO[WindowSystem, Int] =
    for {
      _   <- initWindowSystem
      wid <- createWindow(WindowWidth(300), WindowHeight(300), "Sunkz!")
      _   <- showWindow(wid)
      _   <- loopUntilClosed(wid)
      _   <- destroyWindow(wid)
    } yield 0

}
