package little_server
import little_server._


import java.util.Calendar
import java.util.concurrent.TimeUnit

import akka.actor.Actor

import scala.concurrent.duration.Duration

//implicit
import scala.concurrent.ExecutionContext.Implicits.global

case object Inc

case object Dec

class Counter extends Actor {
  var max = 0


  override def receive: Receive = {
    case Inc => {
      GlobalConfig.current_active += 1
      if (GlobalConfig.current_active > max) {
        max = GlobalConfig.current_active
        GlobalConfig.max_active = max
      }
    }

    case Dec => GlobalConfig.current_active -= 1
  }
}

case object StartPrinter

case object StopPrinter

class Printer extends Actor {
  var state = 0

  case object Print


  def scheduleNextPrint(): Unit = {
    GlobalConfig.system.scheduler.scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), self, Print)
  }

  override def receive: Receive = {
    case StartPrinter =>
      state = 1
      scheduleNextPrint()
    case Print =>
      if (state == 1) {
        println(Calendar.getInstance().getTime() + ": max: " + GlobalConfig.max_active + ", current: " + GlobalConfig.current_active)
        scheduleNextPrint()
      }
    case StopPrinter =>
      state = 0

  }
}