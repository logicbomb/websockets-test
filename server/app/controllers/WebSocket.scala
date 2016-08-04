package controllers

import app.{ChannelManager, UserChannel}
import play.api.libs.iteratee._
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global

class WebSocket extends Controller {
  def index = WebSocket.using[String] { request =>
    // Concurrent.broadcast returns (Enumerator, Concurrent.Channel)
    val join = "(join)(.*)".r
    var room : Option[String] = None
    val (out, c) = Concurrent.broadcast[String]
    val channel = UserChannel(c)

    val in = Iteratee.foreach[String] { msg =>
      msg.toLowerCase() match {
        case join(_, r) =>
          val key = r.trim

          if (room.nonEmpty) {
            ChannelManager.remove(channel)

            ChannelManager.sendAll(key, "a user has left the room")
            room = None
          }

          val num = ChannelManager.add(key, channel)
          if ( num == 1) {
            channel.push(s"You are the first person here")
          } else {
            ChannelManager.sendAll(key) { connected =>
              if (channel.id != connected.id) {
                connected.push(s"A user has joined the room '$key'")
              } else {
                connected.push(s"There are $num users in the room")
              }
            }
          }
          room = Some(key)

        case "exit" =>
          room match {
            case Some(r) =>
              val num = ChannelManager.remove(channel)
              ChannelManager.sendAll(r, s"A user left the channel, there are now $num users")
              channel.eofAndEnd()
              room = None

            case _ =>
              println(s"Not in a room")
          }

        case _ =>
          room match {
            case Some(r) =>
              ChannelManager.sendAll(r) { connected =>
                if (connected.id != channel.id) { connected.push(msg) }
              }

            case None =>
              channel.push("You are not in a room")
          }
      }
    }

    (in, out)
  }
}
