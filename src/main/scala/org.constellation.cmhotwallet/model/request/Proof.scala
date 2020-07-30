package org.constellation.cmhotwallet.model.request

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class Proof(signature: String, `type`: String, verificationMethod: String)

object Proof {
  implicit val proofEncoder: Encoder[Proof] = deriveEncoder
  implicit val proofDecoder: Decoder[Proof] = deriveDecoder
}
