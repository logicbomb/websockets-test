package app

import java.util.UUID

import play.GlobalSettings
import play.api.libs.iteratee.{Input, Concurrent}
import scala.collection.mutable


case class UserChannel[T](id: UUID, private val channel: Concurrent.Channel[T])
extends Concurrent.Channel[T] {
  override def push(chunk: Input[T]): Unit = channel.push(chunk)

  override def end(e: Throwable): Unit = channel.end(e)

  override def end(): Unit = channel.end()
}

object UserChannel {
  def apply[T](channel: Concurrent.Channel[T]): UserChannel[T] =
    UserChannel(UUID.randomUUID(), channel)
}

object ChannelManager {
  private val channels: mutable.Map[String, mutable.Set[UserChannel[String]]] =
    mutable.Map[String, mutable.Set[UserChannel[String]]]()

  private val reverseLookup: mutable.Map[UUID, String] = mutable.Map[UUID, String]()

  def add(key: String, channel: UserChannel[String]): Int = {
    if (!channels.contains(key)) {
      channels += key -> mutable.Set(channel)
    } else {
      channels(key).add(channel)
    }

    if (reverseLookup.contains(channel.id)) {
      reverseLookup.remove(channel.id)
    }

    reverseLookup += channel.id -> key
    channels(key).size
  }

  def remove(channel: UserChannel[String]): Int = {
    if (reverseLookup.contains(channel.id)) {
      val key = reverseLookup(channel.id)
      channels(key).dropWhile {
        _.id == channel.id
      }
      channels(key).size
    }

    0
  }

  def sendAll(key: String, msg: String): Unit = {
    if (channels.contains(key)) {
      channels(key).foreach { c => c.push(msg) }
    }
  }

  def sendAll(key: String)(op: UserChannel[String] => Unit): Unit = {
    if (channels.contains(key)) {
      channels(key).foreach(op)
    }
  }
}

object Global extends GlobalSettings {

}
