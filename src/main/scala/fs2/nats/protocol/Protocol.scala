package fs2.nats.protocol

import java.nio.charset.StandardCharsets

import cats.syntax.either._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto._
import io.circe.syntax._
import io.circe.{parser, Decoder => CirceDecoder, Encoder => CirceEncoder}
import scodec.bits.{BitVector, ByteVector}
import scodec.codecs.{bytes, choice, discriminated, provide}
import scodec.{Attempt, Codec, Err, _}

// https://docs.nats.io/nats-protocol/nats-protocol
object Protocol {

  sealed trait ClientCommand
  object ClientCommand {

    // PONG
    case class Pong() extends ClientCommand
    val pongCodec: Codec[Pong] = new Codec[Pong] {
      override def decode(bits: BitVector): Attempt[DecodeResult[Pong]] =
        Attempt.failure(Err.apply("PONG should not have be sent by the server"))

      override def encode(value: Pong): Attempt[BitVector] =
        Attempt.successful(BitVector("PONG\r\n".getBytes()))

      override def sizeBound: SizeBound = SizeBound.unknown
    }

    // PING
    case class Ping() extends ClientCommand
    val pingCodec: Codec[Ping] = new Codec[Ping] {
      override def decode(bits: BitVector): Attempt[DecodeResult[Ping]] =
        Attempt.failure(Err.apply("PING should not have be sent by the server"))

      override def encode(value: Ping): Attempt[BitVector] =
        Attempt.successful(BitVector("PING\r\n".getBytes()))

      override def sizeBound: SizeBound = SizeBound.unknown
    }

    // UNSUB
    case class UnSub(id: String, maxMessages: Option[Int]) extends ClientCommand
    object UnSub {
      val codec: Codec[UnSub] = new Codec[UnSub] {
        override def decode(bits: BitVector): Attempt[DecodeResult[UnSub]] =
          Attempt.failure(Err.apply("UNSUB should not have be sent by the server"))

        override def encode(value: UnSub): Attempt[BitVector] =
          Attempt.successful(BitVector {
            s"${value.id}${value.maxMessages.map(v => s" $v").getOrElse("")}\r\n"
              .getBytes(StandardCharsets.UTF_8)
          })

        override def sizeBound: SizeBound = SizeBound.unknown
      }
    }

    // SUB
    case class Sub(subject: String, queueGroup: Option[String], sid: String) extends ClientCommand
    object Sub {
      val subCodec = new Codec[Sub] {
        override def decode(bits: BitVector): Attempt[DecodeResult[Sub]] =
          Attempt.failure(Err.apply("Should not be sent by the server"))
        override def encode(value: Sub): Attempt[BitVector] = {
          val message =
            s"${value.subject}${value.queueGroup.map(g => s" $g ").getOrElse(" ")}${value.sid}"
          Attempt.successful(
            BitVector(message.getBytes(StandardCharsets.UTF_8)) ++ crlf.toBitVector)
        }

        override def sizeBound: SizeBound = SizeBound.unknown
      }
    }

    // PUB
    case class Pub(subject: String, replyTo: Option[String], numBytes: Long, payload: ByteVector)
        extends ClientCommand

    object Pub {
      val codec: Codec[Pub] = new Codec[Pub] {
        override def decode(bits: BitVector): Attempt[DecodeResult[Pub]] =
          Attempt.failure(Err.apply("Should not be sent by the server"))

        override def encode(value: Pub): Attempt[BitVector] = {
          val line =
            s"${value.subject}${value.replyTo
              .map(v => s" $v")
              .getOrElse("")} ${value.numBytes}"

          val payload = BitVector(line.getBytes(StandardCharsets.UTF_8)) ++ crlf.toBitVector ++
            value.payload.toBitVector ++ crlf.toBitVector
          Attempt.successful(payload)

        }

        override def sizeBound: SizeBound = SizeBound.unknown
      }

    }

    // CONNECT
    case class Connect(
        version: String,
        authToken: Option[String] = None,
        user: Option[String] = None,
        pass: Option[String] = None,
        name: Option[String] = None,
        lang: String = "scala",
        verbose: Boolean = true,
        pedantic: Boolean = false,
        tlsRequired: Boolean = false,
        protocol: Int = 0,
        echo: Boolean = false
    ) extends ClientCommand

    object Connect {
      implicit val customConfig: Configuration =
        Configuration.default.withSnakeCaseMemberNames.withDefaults
      implicit val connectEncoder = deriveConfiguredEncoder[Connect]

      val codec = new Codec[Connect] {
        override def decode(bits: BitVector): Attempt[DecodeResult[Connect]] =
          Attempt.failure(Err.apply("Should not be sent by the server"))

        override def encode(value: Connect): Attempt[BitVector] =
          Attempt.successful {
            BitVector(
              s"${connectEncoder(value).noSpaces}"
                .getBytes(StandardCharsets.UTF_8)) ++ crlf.toBitVector // \r\n TODO: do we need be explicit
          }

        override def sizeBound: SizeBound = SizeBound.unknown
      }
    }

    val codec: Codec[ClientCommand] =
      discriminated[ClientCommand]
        .by(choice(utf8space, utf8crlf))
        .typecase("SUB", Sub.subCodec)
        .typecase("CONNECT", Connect.codec)
        .typecase("PING", ClientCommand.pingCodec)
        .typecase("PONG", ClientCommand.pongCodec)
        .typecase("PUB", Pub.codec)
        .typecase("UNSUB", UnSub.codec)

  }

  sealed trait ServerCommand
  object ServerCommand {

    // Codecs
    implicit val pongCodec: Codec[Pong] = provide(Pong())
    implicit val pingCodec: Codec[Ping] = provide(Ping())
    implicit val okCodec: Codec[Ok] = provide(Ok())
    implicit val errorCodec: Codec[Error] = utf8crlf.as[Error]
    implicit val msgCodec: Codec[Msg] =
      (utf8space :: utf8space :: utf8crlf.flatPrepend { size =>
        bytes(size.toInt).hlist
      }).as[Msg]
    implicit val infoCodec: Codec[Info] = utf8crlf.exmapc { info: String =>
      Attempt.fromEither(
        parser
          .parse(info)
          .flatMap(_.as[Info])
          .leftMap(e => Err.apply(e.getMessage))
      )
    } { info: Info =>
      Attempt.successful(info.asJson.noSpaces)
    }

    // MSG
    case class Msg(subject: String, id: String, size: String, data: ByteVector)
        extends ServerCommand

    // -ERR
    case class Error(message: String) extends ServerCommand

    // OK
    case class Ok() extends ServerCommand

    // PONG
    case class Pong() extends ServerCommand

    // PING
    case class Ping() extends ServerCommand

    // INFO
    // TODO: use circe to parse snake_case

    // https://docs.nats.io/nats-protocol/nats-protocol#info
    // https://docs.nats.io/nats-protocol/nats-protocol#info
//    @ConfiguredJsonCodec
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
    ) extends ServerCommand

    object Info {
      implicit val customConfig: Configuration =
        Configuration.default.withSnakeCaseMemberNames.withDefaults
      implicit val infoEncoder: CirceEncoder[Info] = deriveConfiguredEncoder[Info]
      implicit val infoDecoder: CirceDecoder[Info] = deriveConfiguredDecoder[Info]
    }
    val codec: Codec[ServerCommand] =
      discriminated[ServerCommand]
        .by(choice(utf8space, utf8crlf))
        .typecase("INFO", ServerCommand.infoCodec)
        .typecase("+OK", ServerCommand.okCodec)
        .typecase("+OK\r\nMSG", ServerCommand.msgCodec)
        .typecase("MSG", ServerCommand.msgCodec)
        .typecase("-ERR", ServerCommand.errorCodec)
        .typecase("MSG", ServerCommand.msgCodec)
        .typecase("PING", ServerCommand.pingCodec)
        .typecase("PONG", ServerCommand.pongCodec)

  }

}
