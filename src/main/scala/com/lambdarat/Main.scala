package com.lambdarat

import com.lambdarat.ui.Window.WindowId
import com.lambdarat.ui.{Window, WindowLive, zwindow}
import org.lwjgl.glfw.GLFW._
import zio.clock.Clock
import zio.{App, Schedule, ZIO}

object Main extends App {

  sealed abstract class GlfwError(val msg: String) extends Exception
  object GlfwError {
    final case object GlfwInitError           extends GlfwError("GLFW Initialization error")
    final case object GlfwWindowCreationError extends GlfwError("GLFW Window creation error")
  }

  override def run(args: List[String]): ZIO[Main.Environment, Nothing, Int] = {
    main
      .provideSome[Environment](
        base =>
          new Window with Clock {
            override def window: Window.Service    = WindowLive.window
            override val clock: Clock.Service[Any] = base.clock
          }
      )
      .fold(_ => 1, identity)
  }

  private def loopUntilWindowClose(windowId: WindowId): ZIO[Clock, Throwable, Boolean] =
    ZIO
      .effect(glfwWindowShouldClose(windowId))
      .tap(shouldClose => ZIO.when(!shouldClose)(ZIO.effect(glfwPollEvents())))
      .repeat(Schedule.doUntil[Boolean](identity))

  def main: ZIO[Window with Clock, Throwable, Int] =
    for {
      _   <- zwindow.initialize
      wid <- zwindow.createWindow(Window.WindowWidth(300), Window.WindowHeight(300), "Hello world!")
      _   <- zwindow.showWindow(wid)
      _   <- loopUntilWindowClose(wid)
      _   <- zwindow.destroyWindow(wid)
    } yield 0

}
