package fs2.nats.serialization

import scodec.bits.ByteVector

trait Deserializer[A] {
  def deserialize(value: ByteVector): A
}
