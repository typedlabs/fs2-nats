package fs2.nats.protocol

import java.nio.charset.StandardCharsets

import io.circe._
import io.circe.generic.extras._
import io.circe.generic.semiauto._
import scodec.bits.{BitVector, ByteVector}

sealed trait Message

// https://docs.nats.io/nats-protocol/nats-protocol
object Message {

  // PONG
  case class Pong() extends Message

  // PING
  case class Ping() extends Message

  // -ERR
  case class Error(message: String) extends Message

  // OK
  case class Ok() extends Message

  // INFO
  // TODO: use circe to parse snake_case
  @ConfiguredJsonCodec
  case class Info(
      serverId: String,
      version: String,
      go: String,
      host: String,
      port: Int,
      maxPayload: Int,
      proto: Option[Int] = None,
      clientId: Option[Long] = None,
      authRequired: Option[Boolean] = None,
      tlsRequired: Option[Boolean] = None,
      tlsVerify: Option[Boolean] = None,
      connectUrls: List[String] = List.empty
  ) extends Message

  object Info {

    implicit val customConfig: Configuration =
      Configuration.default.withSnakeCaseMemberNames.withDefaults

    implicit val infoEncoder: Encoder[Info] = deriveEncoder[Info]
    implicit val infoDecoder: Decoder[Info] =
      deriveDecoder[Info]

  }

  // UNSUB
  case class UnSub(id: String, maxMessages: Option[Int]) extends Message

  object UnSub {
    def encode(value: UnSub): Array[Byte] =
      s"UNSUB ${value.id} ${value.maxMessages.getOrElse("")}\r\n"
        .getBytes(StandardCharsets.UTF_8)
  }

  // SUB
  case class Sub(subject: String, queueGroup: Option[String], sid: String) extends Message

  object Sub {
    def encode(value: Sub): Array[Byte] = {
      val message =
        s"SUB ${value.subject} ${value.queueGroup.getOrElse("")} ${value.sid}\r\n"

      message.getBytes(StandardCharsets.UTF_8)
    }
  }

  // MSG
  case class Msg(subject: String, id: String, size: String, data: ByteVector) extends Message

  // CONNECT
  case class Connect(
      version: String,
      authToken: Option[String] = None,
      user: Option[String] = None,
      pass: Option[String] = None,
      name: Option[String] = None,
      lang: String = "scala",
      verbose: Boolean = false,
      pedantic: Boolean = false,
      tlsRequired: Boolean = false,
      protocol: Int = 0,
      echo: Boolean = false
  ) extends Message

  object Connect {
    implicit val customConfig: Configuration =
      Configuration.default.withSnakeCaseMemberNames.withDefaults

    implicit val connectEncoder: Encoder[Connect] = deriveEncoder[Connect]

    def encode(value: Connect): Array[Byte] =
      s"CONNECT ${connectEncoder(value).noSpaces}\r\n"
        .getBytes(StandardCharsets.UTF_8)

  }

  // PUB
  case class Pub(subject: String, replyTo: Option[String], numBytes: Long, payload: ByteVector)
      extends Message

  object Pub {
    def encode(value: Pub): Array[Byte] = {
      val line =
        s"PUB ${value.subject} ${value.replyTo
          .getOrElse("")} ${value.numBytes}\r\n"

      val payload = BitVector(line.getBytes(StandardCharsets.UTF_8)) ++
        value.payload.toBitVector ++ crlf.toBitVector

      payload.toByteArray

    }
  }

}
