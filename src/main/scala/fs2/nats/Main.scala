package fs2.nats

import java.net.InetSocketAddress
import java.util.concurrent.Executors

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import fs2.Stream
import fs2.nats.client.NatsClient
import fs2.nats.serialization._

import scala.concurrent.duration._

object Main extends IOApp {

  private def subscribe(natsClient: NatsClient[IO]) =
    Stream
      .sleep(2.seconds) >> natsClient
      .subscribe[String]("foo.baz", None, "5")
      .onFinalize(IO.delay(println("SUBSCRIPTION FINISHED")))

  private def publish(natsClient: NatsClient[IO]) =
    Stream
      .awakeEvery[IO](1.seconds)
      .evalMap[IO, Unit](d =>
        natsClient
          .publish("foo.baz", s"${d._1}")
      )
      .onFinalize(IO.delay(println("publish FINISHED")))

  override def run(args: List[String]): IO[ExitCode] = {

    // val address = new InetSocketAddress("demo.nats.io", 4222)
    val address = new InetSocketAddress("127.0.0.1", 4222)

    val client = for {

      _ <- Stream.emit(println("NatsClient MAIN")).covary[IO]

      blocker <- Stream.resource(
        Blocker
          .fromExecutorService[IO](IO.delay(Executors.newCachedThreadPool()))
      )

      _ <- Stream.emit(println("NatsClient BLOCKER")).covary[IO]

      client <- NatsClient.make[IO](address, blocker)

      _ <- Stream.emit(println("NatsClient CLIENT")).covary[IO]

      _ <- publish(client).concurrently(subscribe(client))

    } yield client

    client.compile.drain
      .map(_ => ExitCode.Success)
  }

}
