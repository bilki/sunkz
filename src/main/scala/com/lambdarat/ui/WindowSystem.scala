package com.lambdarat.ui

import java.nio.IntBuffer

import org.lwjgl.glfw.Callbacks._
import org.lwjgl.glfw.GLFW.{glfwInit, _}
import org.lwjgl.glfw.{GLFW, GLFWErrorCallback, GLFWKeyCallback, GLFWVidMode}
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack._
import org.lwjgl.system.MemoryUtil._

import zio.ZIO._
import zio.clock.Clock
import zio.{RIO, Schedule, Task}

import com.lambdarat.Main.GlfwError.{GlfwInitError, GlfwWindowCreationError}
import com.lambdarat.ui.WindowSystem.{WindowHeight, WindowId, WindowWidth}

trait WindowSystem {
  def window: WindowSystem.Service
}

object WindowSystem {
  type WindowId = Long

  final case class WindowHeight(h: Int) extends AnyVal
  final case class WindowWidth(w: Int)  extends AnyVal

  trait Service {
    def initialize: Task[Unit]
    def createWindow(width: WindowWidth, height: WindowHeight, title: String): Task[WindowId]
    def showWindow(windowId: WindowId): Task[Unit]
    def loopUntilClosed(windowId: WindowId): RIO[Clock, Unit]
    def destroyWindow(windowId: WindowId): Task[Unit]
  }

  object Live extends WindowSystem {

    override def window: WindowSystem.Service = new WindowSystem.Service {
      override def initialize: Task[Unit] =
        for {
          _          <- effectTotal(glfwInit).filterOrFail(Predef.identity)(GlfwInitError)
          errPrinter <- effect(GLFWErrorCallback.createPrint(System.err))
          _          <- effect(GLFW.glfwSetErrorCallback(errPrinter))
          _          <- effect(glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)) // hidden after creation
          _          <- effect(glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)) // resizable
        } yield ()

      private def setCloseWindowToEsc(windowId: WindowId): Task[GLFWKeyCallback] =
        effect(glfwSetKeyCallback(windowId, (window, key, _, action, _) => {
          val shouldClose = key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE
          glfwSetWindowShouldClose(window, shouldClose)
        }))

      override def createWindow(
          width: WindowWidth,
          height: WindowHeight,
          title: String
      ): Task[WindowId] =
        for {
          windowId <- effect(glfwCreateWindow(width.w, height.h, title, NULL, NULL))
          _        <- when(windowId == NULL)(fail(GlfwWindowCreationError))
          _        <- setCloseWindowToEsc(windowId)
        } yield windowId

      private def centerWindow(
          windowId: WindowId,
          pWidth: IntBuffer,
          pHeight: IntBuffer,
          vidmode: GLFWVidMode
      ): Task[Unit] =
        effect(
          glfwSetWindowPos(
            windowId,
            (vidmode.width() - pWidth.get(0)) / 2,
            (vidmode.height() - pHeight.get(0)) / 2
          )
        )

      override def showWindow(windowId: WindowId): Task[Unit] =
        bracket[Any, Throwable, MemoryStack, Unit](
          effect(stackPush()),
          _ => effectTotal(stackPop()),
          stack =>
            for {
              pWidth  <- effect(stack.mallocInt(1))
              pHeight <- effect(stack.mallocInt(1))
              _       <- effect(glfwGetWindowSize(windowId, pWidth, pHeight))
              vidmode <- effect(glfwGetVideoMode(glfwGetPrimaryMonitor()))
              _       <- centerWindow(windowId, pWidth, pHeight, vidmode)
              _       <- effect(glfwMakeContextCurrent(windowId))
              _       <- effect(glfwShowWindow(windowId))
            } yield ()
        )

      override def loopUntilClosed(windowId: WindowId): RIO[Clock, Unit] =
        effect(glfwWindowShouldClose(windowId))
          .tap(shouldClose => when(!shouldClose)(effect(glfwPollEvents())))
          .repeat(Schedule.doUntil[Boolean](Predef.identity))
          .unit

      override def destroyWindow(windowId: WindowId): Task[Unit] =
        for {
          _ <- effect(glfwFreeCallbacks(windowId))
          _ <- effect(glfwDestroyWindow(windowId))
          _ <- effect(glfwTerminate())
          _ <- effect(glfwSetErrorCallback(null).free())
        } yield ()
    }
  }
}

object ZWindowSystem {
  def initWindowSystem: RIO[WindowSystem, Unit] = accessM(_.window.initialize)
  def createWindow(
      width: WindowWidth,
      height: WindowHeight,
      title: String
  ): RIO[WindowSystem, WindowId] = accessM(_.window.createWindow(width, height, title))
  def showWindow(windowId: WindowId): RIO[WindowSystem, Unit] =
    accessM(_.window.showWindow(windowId))
  def loopUntilClosed(windowId: WindowId): RIO[WindowSystem with Clock, Unit] =
    accessM(_.window.loopUntilClosed(windowId))
  def destroyWindow(windowId: WindowId): RIO[WindowSystem, Unit] =
    accessM(_.window.destroyWindow(windowId))
}
