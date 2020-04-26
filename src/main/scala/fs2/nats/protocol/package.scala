package fs2.nats

import scodec.bits.{BitVector, ByteVector}
import scodec.codecs._
import scodec.{Attempt, Codec, DecodeResult, Err, SizeBound}

package object protocol {

  // Codecs
  val crlf: ByteVector = ByteVector('\r', '\n')
  val space: ByteVector = ByteVector(0x20)
  val utf8space: Codec[String] = utf8until(space)
  val utf8crlf: Codec[String] = utf8until(crlf)

  private def utf8until(until: ByteVector): Codec[String] = new Codec[String] {
    override def sizeBound: SizeBound = SizeBound.unknown
    override def encode(value: String): Attempt[BitVector] =
      utf8.encode(value).map(_ ++ until.toBitVector)
    override def decode(bits: BitVector): Attempt[DecodeResult[String]] =
      decodeUntil(bits)(until) { bv =>
        scodec.codecs.utf8
          .decodeValue(bv.toBitVector)
          .toEither
          .fold(err => Left(err.message), Right(_))
      }
  }

  private def decodeUntil[A](
      bits: BitVector
  )(until: ByteVector)(decoder: ByteVector => Either[String, A]): Attempt[DecodeResult[A]] = {
    val bv = bits.toByteVector
    val idx = bv.indexOfSlice(until)
    if (idx < 0 || idx > bv.length) {
      Attempt.failure(Err(s"""Cannot find the 0x${until.toHex} bytes to decode until"""))
    } else {
      bv.consume(idx)(decoder) match {
        case Left(_) => Attempt.failure(Err.insufficientBits(idx, bv.size))
        case Right((remainder, l)) =>
          Attempt.successful(
            DecodeResult(l, remainder.drop(until.length).toBitVector)
          )
      }
    }
  }

}
