import java.io.{File, PrintStream}
import java.net.{InetSocketAddress, InetAddress, Socket, ServerSocket}
import java.nio.ByteBuffer
import java.nio.channels._
import java.util.{Calendar, Date}
import java.util.concurrent.TimeUnit

import akka.actor._
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration.Duration

//implicit
import scala.concurrent.ExecutionContext.Implicits.global

object Main {
  def getFile(name: String): String = {
    this.getClass.getResource("/" + name).getFile
  }

  def config(name: String): Config = {
    ConfigFactory.parseFile(new File(name))
  }

  val system = ActorSystem("BasicServerSystem", config("dispatcher-application.conf"))
  //var counter: ActorRef = null
  var max = 0
  var cnt = 0

  final val header_start: String = "HTTP/1.1 200 OK\r\n" +
    "Server: DeathNikServer\r\n" +
    "Content-Type: text/html\r\n" +
    "Content-Length: "
  final val header_end: String = "\r\n" +
    "Connection: close\r\n\r\n"
  var msg: String = null
  var parts: Array[String] = null
  var len: Int = 0
  var selectors: IndexedSeq[Selector] = null


  def main(args: Array[String]) {
    msg = header_start + TolstoyStorage.len + header_end + TolstoyStorage.text
    parts = Main.msg.sliding(1024, 1024).toArray.filter(_ != Nil)
    len = parts.length
    startHttpServer(8091)
  }

  def prosess_requests(sel: Selector, handling_selectors: IndexedSeq[Selector], cur_state: Int, len: Int): Int = {
    val iterator = sel.selectedKeys().iterator
    var cnt = 0
    while (iterator.hasNext) {
      val key: SelectionKey = iterator.next
      iterator.remove()
      if (key.isAcceptable) {
        //println("+isAcceptable")
        val ssChannel: ServerSocketChannel = key.channel().asInstanceOf[ServerSocketChannel]
        val sChannel: SocketChannel = ssChannel.accept()
        sChannel.configureBlocking(false)
        sChannel.register(key.selector(), SelectionKey.OP_READ)
        cnt += 1

        //println("-isAcceptable")
      }
      if (key.isReadable) {

        //println("+isReadable")
        val sChannel: SocketChannel = key.channel.asInstanceOf[SocketChannel]
        val buffer: ByteBuffer = ByteBuffer.wrap(Main.msg.getBytes)
        sChannel.write(buffer)
        sChannel.close()

        //println("-isReadable")
      }
    }
    cur_state + cnt
  }

  def startHttpServer(port: Int) = {
    try {
      //val requestActor = system.actorOf(Props[HttpRequestActor].withDispatcher("my-single-dispatcher"), name = "requestActor")
      //counter = system.actorOf(Props[Counter].withDispatcher("my-default-dispatcher"), name = "counter")
      //val printer = system.actorOf(Props[Printer].withDispatcher("my-default-dispatcher"), name = "printer")
      //printer ! StartPrinter
      val amount = 10

      def create_selector(i: Int): Selector = {
        val selector = Selector.open()
        selector
      }
      selectors = for (i <- 1 to amount) yield create_selector(i)

      for (i <- 1 to amount) {
        val act = system.actorOf(Props[SelectorActor], name = "selectorActor" + i)
        act ! i
      }

      val hostIPAddress: InetAddress = InetAddress.getByName("localhost")
      val port: Int = 8091
      val selector: Selector = Selector.open
      val ssChannel: ServerSocketChannel = ServerSocketChannel.open
      ssChannel.configureBlocking(false)
      ssChannel.socket.bind(new InetSocketAddress(hostIPAddress, port))
      ssChannel.register(selector, SelectionKey.OP_ACCEPT)

      //val server = new ServerSocket(port)
      println(s"BasicServer listening on port $port")
      var state = 0
      while (true) {
        if (selector.select > 0) {
          state = prosess_requests(selector, selectors, state, amount)
        }
        //val requestId = java.util.UUID.randomUUID.toString
        //system.actorOf(Props[HttpResponseActor].withDispatcher("my-default-dispatcher"), name = s"responseActor$requestId") ! socket
      }
    } catch {
      case e: Exception => println(e.getMessage)
    }
  }
}

class SelectorActor extends Actor {
  def prosess_requests(sel: Selector): Unit = {
    val iterator = sel.selectedKeys().iterator
    while (iterator.hasNext) {
      val key: SelectionKey = iterator.next.asInstanceOf[SelectionKey]
      iterator.remove()
      if (key.isReadable) {

        println("+isReadable")
        val sChannel: SocketChannel = key.channel.asInstanceOf[SocketChannel]
        val buffer: ByteBuffer = ByteBuffer.wrap(Main.msg.getBytes)
        sChannel.write(buffer)
        sChannel.close()

        println("-isReadable")
      }
    }
  }


  override def receive: Receive = {
    case i: Int => {
      var loop = true
      while (loop) {
        if (Main.selectors(i - 1).select <= 0) {
          //loop = false
        } else {
          prosess_requests(Main.selectors(i - 1))
        }
      }
      // Main.system.scheduler.scheduleOnce(Duration.create(10, TimeUnit.MILLISECONDS), self, sel)
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
        val responseActor = context.actorOf(Props[HttpResponseActor].withDispatcher("my-default-dispatcher"), name = s"responseActor$requestId")
        responseActor !(sock, 0)
      } catch {
        case e: Exception => println(e.getMessage)
      }
  }
}

case class CheckSocket(sock: Socket)

class HttpResponseActor extends Actor {
  final val header_start: String = "HTTP/1.1 200 OK\r\n" +
    "Server: DeathNikServer\r\n" +
    "Content-Type: text/html\r\n" +
    "Content-Length: "
  final val header_end: String = "\r\n" +
    "Connection: close\r\n\r\n"

  override def receive: Actor.Receive = {
    case (sock: Socket, tick: Int) =>
      try {

        new PrintStream(sock.getOutputStream).println(Main.parts(tick))
        if (tick + 1 >= Main.len) {
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
          Main.system.scheduler.scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), self, CheckSocket(sock))
      } catch {
        case e: Exception => done(sock)
      }
  }

  def done(sock: Socket) {
    //Main.counter ! Dec
    sock.close()
    self ! PoisonPill
  }
}
