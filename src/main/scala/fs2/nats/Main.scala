package fs2.nats

import java.net.InetSocketAddress
import java.util.concurrent.Executors
import java.util.logging.Logger

import cats.effect.{Blocker, ExitCode, IO, IOApp}
import fs2.Stream
import fs2.nats.client.NatsClient

import scala.concurrent.duration._

object Main extends IOApp {
  val logger = Logger.getLogger("Main")

  private def subscribe(nc: NatsClient[IO], subject: String) =
    Stream
      .sleep(2.seconds) >> nc
      .subscribe[String](subject, None, "main")
      .debug()
      .onFinalize(IO.delay(logger.info("SUBSCRIPTION FINISHED")))

  private def publish(nc: NatsClient[IO], subject: String) =
    Stream
      .awakeEvery[IO](5.seconds)
      .evalMap[IO, Unit](d =>
        nc.publish(subject, s"${d._1}"))
      .onFinalize(IO.delay(logger.info("publish FINISHED")))

  override def run(args: List[String]): IO[ExitCode] = {
    // val address = new InetSocketAddress("demo.nats.io", 4222)
    val address = new InetSocketAddress("127.0.0.1", 4222)

    val client = for {

      _ <- Stream.emit(logger.info("NatsClient MAIN")).covary[IO]

      blocker <- Stream.resource(
        Blocker
          .fromExecutorService[IO](IO.delay(Executors.newCachedThreadPool()))
      )

      _ <- Stream.emit(logger.info("NatsClient BLOCKER")).covary[IO]

      client <- NatsClient.make[IO](address, blocker)

      _ <- Stream.emit(logger.info("NatsClient CLIENT")).covary[IO]

      _ <- subscribe(client, "s1")
        .concurrently(publish(client, "s1"))
        .concurrently(subscribe(client, "s2").concurrently(publish(client, "s2")))
//      _ <- subscribe(client, "s2").concurrently(publish(client, "s2"))

    } yield client

    client.compile.drain
      .map(_ => ExitCode.Success)
  }

//   val n = 1000000
//    for {
//      start <- IO(System.nanoTime())
//      q <- fs2.concurrent.Queue.bounded[IO, Int](n)
//      _ <- IO(println(s"enqueing"))
//      _ <- (0 until n).map(i => q.offer1(i)).toList.sequence
//      time <- IO(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start))
////      size <- q.getSize
////      _ <- q.getSize.flatMap(_ => IO(println(s"queue size= ${size}; time = $time ms")))
//      _ <- IO(println(s"queue size= ; time = $time ms"))
//    } yield {
//      println("EXIT")
//      ExitCode.Success
//    }

}
