package fs2.nats.client

import java.net.{ConnectException, InetSocketAddress}

import cats.effect.Timer
import fs2.RaiseThrowable
import fs2.io.tcp.Socket
import fs2.nats.protocol.Protocol.ClientCommand.{Connect, Ping}
import fs2.nats.protocol.Protocol.ServerCommand.Info
import scodec.stream.{StreamDecoder, StreamEncoder}
// import fs2.io.tls.TLSContext
import fs2.nats.protocol.Protocol.ClientCommand.{Pub, Sub}
import fs2.nats.protocol.Protocol.{ClientCommand, ServerCommand}
import fs2.nats.serialization.Serializer

import scala.concurrent.duration._
// import java.util.concurrent.ConcurrentHashMap
import cats.effect.concurrent.Ref
import cats.effect.{Blocker, Concurrent, ContextShift}
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import fs2.io.tcp.SocketGroup
import fs2.nats.protocol._
import fs2.nats.serialization.Deserializer
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import scodec.bits.ByteVector

trait ServerCommandHandler[F[_]] {
  def handle(serverEvent: ServerCommand): F[Unit]
}

trait INatsClient[F[_]] {
  def subscribe[A: Deserializer](subject: String,
                                 queueGroup: Option[String],
                                 id: String): Stream[F, A]
  def publish[A: Serializer](subject: String, data: A): F[Unit]
}

class NatsClient[F[_]: ContextShift: RaiseThrowable: Timer] private (
    socket: Socket[F],
    logger: Logger[F],
    subscribers: Ref[F, Map[String, Queue[F, ByteVector]]],
    // tlsContext: Option[TLSContext], // TODO: get rid of var for Ref[...]
    outgoingQueue: Queue[F, ClientCommand]
//    messageSocket: MessageSocket[F, Protocol.ServerCommand, Protocol.ClientCommand],
)(implicit val F: Concurrent[F])
    extends INatsClient[F] {

//  private def wildcardFilter(subject1: String, subject2: String) =
//    // TODO: fix this to match https://docs.nats.io/nats-protocol/nats-protocol#protocol-conventions
//    // namely Wildcards
//    // subject1 == subject2
//    true

  private def handler(serverCommand: ServerCommand): F[Unit] = serverCommand match {
    case msg: Protocol.ServerCommand.Info =>
      //      info = Some(msg)
      // val connect = Connect("0.0.1")
      //  .flatMap(_ => socket.write(Chunk.bytes(Connect.encode(connect))))
      logger.info(s"INFO $msg")
    case msg: Protocol.ServerCommand.Msg =>
      val res = for {
        subs <- subscribers.get.map { map =>
          map
            .filter(_._1 == msg.id)
            .values
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

    case _: Protocol.ServerCommand.Ok      => logger.debug(s"+OK")
    case msg: Protocol.ServerCommand.Error => logger.error(s"-ERR $msg")
    case _: Protocol.ServerCommand.Ping =>
      logger.debug(s"PING") >>
        outgoingQueue.enqueue1(ClientCommand.Pong())
    case _: Protocol.ServerCommand.Pong => logger.debug(s"PONG")
    case msg                            => logger.warn(s"UNKNOWN Message: $msg")
  }

  private def processIncoming: Stream[F, Unit] =
    Stream.eval(socket.isOpen.map(open => println(s"Is Socket open ${open}"))) >>
      socket
        .reads(8192)
        .through(StreamDecoder.many(ServerCommand.codec).toPipeByte[F])
        .debug(s => s"->> $s")
        .evalMap(handler) ++ processIncoming

  private def processOutgoing: Stream[F, Unit] =
    outgoingQueue.dequeue
      .debug(s => s"<<- $s")
      .through(StreamEncoder.many(ClientCommand.codec).toPipeByte)
      .through(socket.writes(None))

  private def write(cmd: ClientCommand): Stream[F, Unit] =
    Stream(cmd)
      .covary[F]
      .through(StreamEncoder.many(ClientCommand.codec).toPipeByte)
      .through(socket.writes(None))

  def start: Stream[F, Unit] =
    processIncoming.drain
      .concurrently(processOutgoing)
      .concurrently(Stream.awakeEvery[F](5.seconds).evalMap(_ => outgoingQueue.enqueue1(Ping())))

  def subscribe[A: Deserializer](subject: String,
                                 queueGroup: Option[String],
                                 id: String): Stream[F, A] = {

    val sub = Sub(subject, queueGroup, id)
    val deserializer = implicitly[Deserializer[A]]
    for {
      _ <- Stream.eval(logger.debug(s"Subscribing to $subject subject"))
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

      stream <- activeQueue.dequeue
        .map(deserializer.deserialize)
        .onFinalize {
          logger.info(s"Subscription to $subject finalized")
        }
        .concurrently(write(sub))

    } yield stream
  }

  def publish[A](subject: String, data: A)(implicit serializer: Serializer[A]): F[Unit] = {
    val serialized = serializer.serialize(data)
    val message = Pub(subject, None, serialized.length, serialized)
    outgoingQueue.enqueue1(message)
  }

}

object NatsClient {

  def make[F[_]: ContextShift: Timer](address: InetSocketAddress, blocker: Blocker)(
      implicit F: Concurrent[F]
  ): Stream[F, NatsClient[F]] = {

    // TODO: improve add TLSSocket option once
    // https://github.com/functional-streams-for-scala/fs2/pull/1717

    val subscribers = Ref.of(Map.empty[String, Queue[F, ByteVector]])
    val logger = Slf4jLogger.getLogger[F]

    val connect =
      for {

        subscribers <- Stream.eval(subscribers)

        _ <- Stream.eval(logger.info("Initializing NatsClient"))

        socketGroup <- Stream.resource(SocketGroup[F](blocker))

        socket = socketGroup
          .client[F](address, reuseAddress = true, keepAlive = true, noDelay = false)

        nonTlsSocket <- Stream.resource(socket)

        nonTlsMessageSocket <- Stream.eval(
          MessageSocket(
            nonTlsSocket,
            Protocol.ServerCommand.codec,
            Protocol.ClientCommand.codec,
            1024
          ))

        _ <- nonTlsMessageSocket.read.evalMap {
          case info: Info =>
            logger.info(s"Nats Info: $info").map(_ => info)
          case msg =>
            F.raiseError[Info](
              new RuntimeException(s"Received nats msg: $msg before completing connection")
            )
        }

        _ <- Stream.eval(nonTlsMessageSocket.write1(Connect("0.1.2")))

        outgoingQueue <- Stream.eval(Queue.bounded[F, ClientCommand](1024))

        client = new NatsClient[F](nonTlsSocket, logger, subscribers, outgoingQueue)

        _ <- Stream(client)
          .covary[F]
          .concurrently(client.start
            .handleErrorWith { e =>
              Stream.eval(logger.error(e)(s"Client socket failed")) >>
                client.start
            })

      } yield client

    connect
      .handleErrorWith {
        case _: ConnectException =>
          val retryDelay = 5.seconds
          Stream.eval(F.delay(println(s"Failed to connect. Retrying in $retryDelay."))) >>
            connect
              .delayBy(retryDelay)
        case e =>
          Stream.eval(F.raiseError(e))
      }

  }
}
