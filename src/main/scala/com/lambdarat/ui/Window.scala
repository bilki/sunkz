package com.lambdarat.ui

import java.nio.IntBuffer

import com.lambdarat.Main.GlfwError.{GlfwInitError, GlfwWindowCreationError}
import com.lambdarat.ui.Window.{WindowHeight, WindowId, WindowWidth}
import org.lwjgl.glfw.GLFW.{glfwInit, _}
import org.lwjgl.glfw.{GLFW, GLFWErrorCallback, GLFWKeyCallback, GLFWVidMode}
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryStack._
import org.lwjgl.system.MemoryUtil._
import org.lwjgl.glfw.Callbacks._

import zio.{Task, ZIO}

object Window {
  type WindowId = Long

  final case class WindowHeight(h: Int) extends AnyVal
  final case class WindowWidth(w: Int)  extends AnyVal

  trait Service {
    def initialize: Task[Unit]
    def createWindow(width: WindowWidth, height: WindowHeight, title: String): Task[WindowId]
    def showWindow(windowId: WindowId): Task[Unit]
    def destroyWindow(windowId: WindowId): Task[Unit]
  }
}

trait Window {
  def window: Window.Service
}

object zwindow {
  def initialize: ZIO[Window, Throwable, Unit] = ZIO.accessM(_.window.initialize)
  def createWindow(
      width: WindowWidth,
      height: WindowHeight,
      title: String
  ): ZIO[Window, Throwable, WindowId] = ZIO.accessM(_.window.createWindow(width, height, title))
  def showWindow(windowId: WindowId): ZIO[Window, Throwable, Unit] =
    ZIO.accessM(_.window.showWindow(windowId))
  def destroyWindow(windowId: WindowId): ZIO[Window, Throwable, Unit] =
    ZIO.accessM(_.window.destroyWindow(windowId))
}

trait WindowLive extends Window {

  override def window: Window.Service = new Window.Service {
    override def initialize: Task[Unit] =
      for {
        _          <- ZIO.effectTotal(glfwInit).filterOrFail(identity)(GlfwInitError)
        errPrinter <- ZIO.effect(GLFWErrorCallback.createPrint(System.err))
        _          <- ZIO.effect(GLFW.glfwSetErrorCallback(errPrinter))
        _          <- ZIO.effect(glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE)) // hidden after creation
        _          <- ZIO.effect(glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE)) // resizable
      } yield ()

    private def setCloseWindowToEsc(windowId: WindowId): Task[GLFWKeyCallback] =
      ZIO.effect(glfwSetKeyCallback(windowId, (window, key, _, action, _) => {
        val shouldClose = key == GLFW_KEY_ESCAPE && action == GLFW_RELEASE
        glfwSetWindowShouldClose(window, shouldClose)
      }))

    override def createWindow(
        width: WindowWidth,
        height: WindowHeight,
        title: String
    ): Task[WindowId] =
      for {
        windowId <- ZIO.effect(glfwCreateWindow(width.w, height.h, title, NULL, NULL))
        _        <- ZIO.when(windowId == NULL)(ZIO.fail(GlfwWindowCreationError))
        _        <- setCloseWindowToEsc(windowId)
      } yield windowId

    private def centerWindow(
        windowId: WindowId,
        pWidth: IntBuffer,
        pHeight: IntBuffer,
        vidmode: GLFWVidMode
    ): Task[Unit] =
      ZIO.effect(
        glfwSetWindowPos(
          windowId,
          (vidmode.width() - pWidth.get(0)) / 2,
          (vidmode.height() - pHeight.get(0)) / 2
        )
      )

    override def showWindow(windowId: WindowId): Task[Unit] =
      ZIO.bracket[Any, Throwable, MemoryStack, Unit](
        ZIO.effect(stackPush()),
        _ => ZIO.effectTotal(stackPop()),
        stack =>
          for {
            pWidth  <- ZIO.effect(stack.mallocInt(1))
            pHeight <- ZIO.effect(stack.mallocInt(1))
            _       <- ZIO.effect(glfwGetWindowSize(windowId, pWidth, pHeight))
            vidmode <- ZIO.effect(glfwGetVideoMode(glfwGetPrimaryMonitor()))
            _       <- centerWindow(windowId, pWidth, pHeight, vidmode)
            _       <- ZIO.effect(glfwMakeContextCurrent(windowId))
            _       <- ZIO.effect(glfwShowWindow(windowId))
          } yield ()
      )

    override def destroyWindow(windowId: WindowId): Task[Unit] =
      for {
        _ <- ZIO.effect(glfwFreeCallbacks(windowId))
        _ <- ZIO.effect(glfwDestroyWindow(windowId))
        _ <- ZIO.effect(glfwTerminate())
        _ <- ZIO.effect(glfwSetErrorCallback(null).free())
      } yield ()
  }
}

object WindowLive extends WindowLive
