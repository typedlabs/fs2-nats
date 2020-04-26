package fs2.nats.client

import cats.effect.Concurrent
import cats.implicits._
import fs2.Stream
import fs2.concurrent.Queue
import fs2.io.tcp.Socket
import scodec.stream.{StreamDecoder, StreamEncoder}
import scodec.{Decoder, Encoder}

/**
  * Socket which reads a stream of messages of type `In` and allows writing
  * messages of type `Out`.
  */
trait MessageSocket[F[_], In, Out] {
  def read: Stream[F, In]
  def write1(out: Out): F[Unit]
}

object MessageSocket {

  def apply[F[_]: Concurrent, In, Out](
      socket: Socket[F],
      inDecoder: Decoder[In],
      outEncoder: Encoder[Out],
      outputBound: Int
  ): F[MessageSocket[F, In, Out]] =
    for {
      outgoing <- Queue.bounded[F, Out](outputBound)
    } yield
      new MessageSocket[F, In, Out] {
        def read: Stream[F, In] = {
          def readSocket: Stream[F, In] =
            Stream.eval(socket.isOpen.map(open => println(s"Is Socket open ${open}"))) >>
              socket
                .reads(8192)
                .through(StreamDecoder.many(inDecoder).toPipeByte[F])
                .debug(s => s"->> $s")
                .onFinalize(Concurrent[F].delay(println("readSocket FINISHED"))) ++ readSocket

          val writeOutput = outgoing.dequeue
            .debug(s => s"<<- $s")
            .through(StreamEncoder.many(outEncoder).toPipeByte)
            .through(socket.writes(None))

          readSocket.concurrently(writeOutput)
        }
        def write1(out: Out): F[Unit] = outgoing.enqueue1(out)
      }
}
