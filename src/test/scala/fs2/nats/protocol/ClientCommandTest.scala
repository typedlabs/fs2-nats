package fs2.nats.protocol

import org.scalatest.flatspec.AnyFlatSpec

class ClientCommandTest extends AnyFlatSpec {
  "ClientCommand" should "encode Sub messages" in {
    import fs2.nats.protocol.Protocol.ClientCommand.{Sub, codec}

    val r = codec.encode(Sub("subject", None, "sid"))
    println(r)
    println("====")
    println(new String(r.toOption.get.toByteArray))
    println("====")

    assert(Sub.subCodec.decode(r.toOption.get).isFailure)

    val r3 = codec.encode(Sub("subject", Some("queueGroup"), "sid"))
    println(r3)
    println("====")
    println(new String(r3.toOption.get.toByteArray))
    println("====")

    assert(r3.isSuccessful)

  }
}
