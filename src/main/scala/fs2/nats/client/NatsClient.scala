package fs2.nats.client

import java.net.InetSocketAddress
import java.nio.charset.Charset

// import java.util.concurrent.ConcurrentHashMap

import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Concurrent, ContextShift}
import cats.implicits._
import fs2.concurrent.Queue
import fs2.io.tcp.{Socket, SocketGroup}
import fs2.nats.protocol.Message._
import fs2.nats.protocol.{Message, ProtocolParser}
import fs2.nats.serialization.{Deserializer, Serializer}
import fs2.{Chunk, Stream}
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scodec.bits.ByteVector
import scodec.stream.StreamDecoder

class NatsClient[F[_]: ContextShift] private (
    frames: StreamDecoder[Message],
    socketClient: Socket[F],
    logger: Logger[F],
    subscribers: Ref[F, Map[String, Queue[F, ByteVector]]],
    private var info: Info
)(implicit val F: Concurrent[F]) {

  private def wildcardFilter(subject1: String, subject2: String) =
    // TODO: fix this to match https://docs.nats.io/nats-protocol/nats-protocol#protocol-conventions
    // namely Wildcards
    subject1 == subject2

  private def socketReader(socket: Socket[F])(message: Message): F[Unit] =
    message match {
      case msg: Message.Msg =>
        val res = for {
          subs <- subscribers.get.map { map =>
            map.view
              .filter { case (subject, _) => wildcardFilter(subject, msg.subject) }
              .map { case (_, queue) => queue }
              .toList
          }
          enqueued <- F.delay {
            subs.map { queue =>
              queue.enqueue1(msg.data)
            }
          }
          res <- enqueued.traverse(identity)

        } yield { res }

        res.flatMap(_ => logger.info(s"Received: $msg"))

      case _: Message.Ok =>
        logger.debug(s"+OK")

      case msg: Message.Error =>
        logger.error(s"-ERR $msg")

      case msg: Message.Info =>
        info = msg
        // val connect = Connect("0.0.1")
        //  .flatMap(_ => socket.write(Chunk.bytes(Connect.encode(connect))))
        logger.info(s"INFO $msg")

      case _: Message.Ping =>
        logger.debug(s"PING") >>
          socket
            .write(Chunk.bytes("PONG\r\n".getBytes(Charset.forName("ASCII"))))
            .flatMap(_ => logger.debug("PONG"))

      case msg =>
        logger.warn(s"UNKNOWN Message: $msg")
    }

  private def readerStream(socket: Socket[F]): Stream[F, Unit] =
    Stream
      .eval(
        socketClient.isOpen.flatMap(isOpen => logger.info(s"readerStream: Socket is open: $isOpen"))
      ) >>
      Stream.eval(logger.info("Reading socket stream")) ++
        socket
          .reads(1024)
          .through(frames.toPipeByte)
          .evalMap(socketReader(socket)) ++
        readerStream(socket) // Finish reading, let`s recurse

  def start: Stream[F, Unit] =
    for {
      _ <- Stream.eval(logger.info("Starting NatsClient"))
      _ <- readerStream(socketClient).handleErrorWith { e =>
        println(e)
        readerStream(socketClient)
      }
    } yield ()

  def subscribe[A](subject: String, queueGroup: Option[String], id: String)(
      implicit deserializer: Deserializer[A]
  ): Stream[F, A] = {

    val sub = Sub(subject, queueGroup, id)

    for {

      _ <- Stream.eval(logger.debug(s"Subscribed to $subject subject"))

      activeQueue <- Stream.eval {
        Queue.unbounded[F, ByteVector].flatMap { queue =>
          subscribers
            .modify { in =>
              if (in.contains(subject)) {
                (in, in)
              } else {
                (in + (subject -> queue), in)
              }
            }
            .as(queue)
        }

      }

      _ <- Stream.eval(socketClient.write(Chunk.bytes(Sub.encode(sub))))

      stream <- activeQueue.dequeue
        .map(deserializer.deserialize)
        .onFinalize {
          logger.info(s"Subscription to $subject finalized")
        }

    } yield stream
  }

  def publish[A](subject: String, data: A)(implicit serializer: Serializer[A]): F[Unit] = {
    val serialized = serializer.serialize(data)
    val message = Pub(subject, None, serialized.length, serialized)
    val messageChunk = Chunk.bytes(Pub.encode(message))

    socketClient.isOpen.flatMap(isOpen => logger.info(s"publish: Socket is open: $isOpen")) >>
      socketClient
        .write(messageChunk)
        .flatMap(_ => logger.info(s"Published: $message"))
  }

}

object NatsClient {

  def make[F[_]: ContextShift](address: InetSocketAddress, blocker: Blocker)(
      implicit F: Concurrent[F]
  ): Stream[F, NatsClient[F]] = {

    // TODO: improve add TLSSocket option once
    // https://github.com/functional-streams-for-scala/fs2/pull/1717

    val subscribers = Ref.of(Map.empty[String, Queue[F, ByteVector]])

    val logger = Slf4jLogger.getLogger[F]

    val frames = StreamDecoder.once(ProtocolParser.protocolCodec)

    for {
      subscribers <- Stream.eval(subscribers)

      _ <- Stream.eval(logger.info("Initializing NatsClient"))

      socketGroup <- Stream.resource(SocketGroup[F](blocker))

      socket = socketGroup
        .client[F](address, reuseAddress = true, keepAlive = false, noDelay = false)

      nonTlsSocket <- Stream.resource(socket)

      info <- nonTlsSocket.reads(1024).through(frames.toPipeByte).evalMap {
        case info: Info =>
          logger.info(s"Nats Info: $info").map(_ => info)
        case msg =>
          F.raiseError[Info](
            new RuntimeException(s"Received nats msg: $msg before completing connection")
          )
      }

      client = new NatsClient[F](frames, nonTlsSocket, logger, subscribers, info)

      _ <- Stream(client)
        .covary[F]
        .concurrently(
          client.start
            .handleErrorWith { e =>
              Stream.eval(logger.error(e)(s"Client socket failed")) >>
                client.start
            }
        )

    } yield client

  }
}
