package fs2.nats
import java.nio.charset.StandardCharsets

import scodec.bits.ByteVector

package object serialization {

  implicit val stringDeserializer: Deserializer[String] =
    new Deserializer[String] {
      override def deserialize(value: ByteVector): String =
        new String(value.toArray, StandardCharsets.UTF_8)
    }

  implicit val stringSerializer: Serializer[String] =
    new Serializer[String] {
      override def serialize(value: String): ByteVector =
        ByteVector.apply(value.getBytes(StandardCharsets.UTF_8))
    }

}
