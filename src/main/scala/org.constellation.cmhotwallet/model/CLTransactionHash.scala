package org.constellation.cmhotwallet.model

import io.circe.Decoder

case class CLTransactionHash(value: String)

object CLTransactionHash {
  implicit val clTransactionHashDecoder: Decoder[CLTransactionHash] = Decoder.decodeString.map(CLTransactionHash(_))
}
