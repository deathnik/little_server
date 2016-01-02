import java.io.PrintStream
import java.net.{Socket, ServerSocket}
import java.util.{Calendar, Date}
import java.util.concurrent.TimeUnit

import akka.actor._

import scala.concurrent.duration.Duration

//implicit
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  val system = ActorSystem("BasicServerSystem")
  var counter: ActorRef = null
  var max = 0
  var cnt = 0

  def main(args: Array[String]) {
    startHttpServer(8091)
  }

  def startHttpServer(port: Int) = {
    try {
      val requestActor = system.actorOf(Props[HttpRequestActor], name = "requestActor")
      counter = system.actorOf(Props[Counter], name = "counter")
      val printer = system.actorOf(Props[Printer], name = "printer")
      printer ! StartPrinter


      val server = new ServerSocket(port)
      println(s"BasicServer listening on port $port")
      while (true) {
        val socket = server.accept()
        counter ! Inc
        requestActor ! socket
      }
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}

case object StartPrinter

case object StopPrinter

case object Print


class Printer extends Actor {
  var state = 0

  def scheduleNextPrint(): Unit = {
    Main.system.scheduler.scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), self, Print)
  }

  override def receive: Receive = {
    case StartPrinter =>
      state = 1
      scheduleNextPrint()
    case Print =>
      if (state == 1) {
        println(Calendar.getInstance().getTime() + ": max: " + Main.max + ", current: " + Main.cnt)
        scheduleNextPrint()
      }
    case StopPrinter =>
      state = 0

  }

}

case object Inc

case object Dec

class Counter extends Actor {
  var max = 0

  override def receive: Receive = {
    case Inc => {
      Main.cnt += 1
      if (Main.cnt > max) {
        max = Main.cnt
        Main.max = max
      }
    }

    case Dec => Main.cnt -= 1
  }
}

class HttpRequestActor extends Actor {

  override def receive: Receive = {
    case sock: Socket =>
      try {
        val requestId = java.util.UUID.randomUUID.toString
        val responseActor = context.actorOf(Props[HttpResponseActor], name = s"responseActor$requestId")
        responseActor ! sock
      } catch {
        case e: Exception => println(e.getMessage)
      }
  }
}

case object CheckSocket

class HttpResponseActor extends Actor {
  final val header_start: String = "HTTP/1.1 200 OK\r\n" +
    "Server: DeathNikServer\r\n" +
    "Content-Type: text/html\r\n" +
    "Content-Length: "
  final val header_end: String = "\r\n" +
    "Connection: close\r\n\r\n"
  var sock: Socket = null

  override def receive: Actor.Receive = {
    case sock: Socket =>
      try {
        this.sock = sock
        val out = new PrintStream(sock.getOutputStream)
        val msg = TolstoyStorage.text
        val msg_len = TolstoyStorage.len
        out.println(s"$header_start$msg_len$header_end$msg")
        sock.shutdownOutput()
        self ! CheckSocket
      } catch {
        case e: Exception =>
          println(e.getMessage)
          done()
      }
    case CheckSocket =>
      try {
        val available = sock.getInputStream.available()
        sock.getInputStream.skip(available)

        if (sock.getInputStream.read() == -1)
          done()
        else
          Main.system.scheduler.scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), self, CheckSocket)
      } catch {
        case e: Exception =>done()
      }
  }

  def done() {
    Main.counter ! Dec
    sock.close()
    self ! PoisonPill
  }
}
