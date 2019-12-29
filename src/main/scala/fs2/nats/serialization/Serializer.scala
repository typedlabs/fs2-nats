package fs2.nats.serialization

import scodec.bits.ByteVector

trait Serializer[A] {
  def serialize(value: A): ByteVector
}
