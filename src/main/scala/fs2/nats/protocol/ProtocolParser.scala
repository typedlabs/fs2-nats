package fs2.nats.protocol

import fs2.nats.protocol.Message._
import scodec._
import scodec.codecs._

object ProtocolParser {

  val protocolCodec: Codec[Message] =
    discriminated[Message]
      .by(choice(utf8space, utf8crlf))
      .subcaseO("INFO") {
        case fld: Info => Some(fld)
        case _         => None
      }(infoCodec)
      .subcaseO("+OK") {
        case fld: Ok => Some(fld)
        case _       => None
      }(okCodec)
      .subcaseO("+OK\r\nMSG") { // hack to be able to subscribe and publish to same subject in same socket
        case fld: Msg => Some(fld)
        case _        => None
      }(msgCodec)
      .subcaseO("-ERR") {
        case fld: Error => Some(fld)
        case _          => None
      }(errorCodec)
      .subcaseO("MSG") {
        case fld: Msg => Some(fld)
        case _        => None
      }(msgCodec)
      .subcaseO("PING") {
        case fld: Ping => Some(fld)
        case _         => None
      }(pingCodec)
      .subcaseO("PONG") {
        case fld: Pong => Some(fld)
        case _         => None
      }(pongCodec)

}
