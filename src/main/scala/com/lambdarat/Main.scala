package com.lambdarat

import com.lambdarat.Main.GlfwError.{GlfwInitError, GlfwShowWindowError, GlfwWindowCreationError}
import org.lwjgl.glfw.GLFW._
import org.lwjgl.glfw.GLFWErrorCallback
import org.lwjgl.system.MemoryUtil._
import zio.console._
import zio.{App, IO, ZIO}

object Main extends App {

  sealed abstract class GlfwError(val msg: String) extends Exception
  object GlfwError {
    final case object GlfwInitError           extends GlfwError("GLFW Initialization error")
    final case object GlfwWindowCreationError extends GlfwError("GLFW Window creation error")
    final case class GlfwShowWindowError(window: Long)
        extends GlfwError(s"GLFW Error showing window ${window}")
  }

  override def run(args: List[String]): ZIO[Main.Environment, Nothing, Int] =
    for {
      _ <- initialize
        .catchAll(err => putStrLn(s"[SUNK] Failed with ${err.msg}") *> ZIO.succeed(1))
        .fork
      _ <- ZIO.never
    } yield 0

  val setupErrorCallback: ZIO[Any, GlfwError.GlfwInitError.type, GLFWErrorCallback] =
    ZIO.effect(GLFWErrorCallback.createPrint(System.err).set()).mapError(_ => GlfwInitError)
  val initializeGlfw: ZIO[Any, GlfwError.GlfwInitError.type, Boolean] =
    ZIO.effectTotal(glfwInit).filterOrFail(identity)(GlfwInitError)
  val createWindow: ZIO[Any, GlfwError.GlfwWindowCreationError.type, Long] = ZIO
    .effect(glfwCreateWindow(300, 300, "Hello World!", NULL, NULL))
    .mapError(_ => GlfwWindowCreationError)
  def showWindow(window: Long): ZIO[Any, GlfwShowWindowError, Unit] =
    ZIO.effect(glfwShowWindow(window)).mapError(_ => GlfwShowWindowError(window))

  val initialize: IO[GlfwError, Int] = for {
    _      <- setupErrorCallback
    _      <- initializeGlfw
    window <- createWindow
    _      <- showWindow(window)
  } yield 0

}
