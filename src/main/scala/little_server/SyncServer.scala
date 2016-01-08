package little_server

import java.io.PrintStream
import java.net.{ServerSocket, Socket}
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, PoisonPill, Props}

import scala.concurrent.duration.Duration

//implicit
import scala.concurrent.ExecutionContext.Implicits.global

object SyncServer{
  def apply():SyncServer = {
    new SyncServer
  }
}

class SyncServer {
  def startHttpServer(port: Int) = {
    try {

      val requestActor = GlobalConfig.system.actorOf(Props[HttpRequestActor].withDispatcher("my-single-dispatcher"), name = "requestActor")
      GlobalConfig.counter = GlobalConfig.system.actorOf(Props[Counter].withDispatcher("my-default-dispatcher"), name = "counter")
      val printer = GlobalConfig.system.actorOf(Props[Printer].withDispatcher("my-default-dispatcher"), name = "printer")

      if (GlobalConfig.allowPrintingStats)
        printer ! StartPrinter

      val server_socket = new ServerSocket(port)
      println(s"BasicSyncServer listening on port $port")
      while (true) {
        val socket = server_socket.accept()
        requestActor ! socket
      }
    }
    catch {
      case e: Exception =>
        println("Exception in server main, exiting:")
        println(e.getMessage)
    }
  }
}

class HttpRequestActor extends Actor {

  override def receive: Receive = {
    case sock: Socket =>
      try {
        val requestId = java.util.UUID.randomUUID.toString
        val responseActor = context.actorOf(Props[HttpResponseActor].withDispatcher("my-default-dispatcher"), name = s"responseActor$requestId")
        responseActor !(sock, 0)
      } catch {
        case e: Exception => println(e.getMessage)
      }
  }
}

class HttpResponseActor extends Actor {

  case class CheckSocket(sock: Socket)

  override def receive: Actor.Receive = {
    case (sock: Socket, tick: Int) =>
      try {

        new PrintStream(sock.getOutputStream).println(GlobalConfig.msg_parts(tick))
        if (tick + 1 >= GlobalConfig.msg_parts_len) {
          sock.shutdownOutput()
          self ! CheckSocket(sock)
        } else {
          self !(sock, tick + 1)
        }

      } catch {
        case e: Exception =>
          println(e.getMessage)
          done(sock)
      }
    case CheckSocket(sock: Socket) =>
      try {
        val available = sock.getInputStream.available()
        sock.getInputStream.skip(available)

        if (sock.getInputStream.read() == -1)
          done(sock)
        else
          GlobalConfig.system.scheduler.scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), self, CheckSocket(sock))
      } catch {
        case e: Exception => done(sock)
      }
  }

  def done(sock: Socket) {
    GlobalConfig.counter ! Dec
    sock.close()
    self ! PoisonPill
  }
}