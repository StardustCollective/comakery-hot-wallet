package org.constellation.cmhotwallet.model.request

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class CMRequest(body: Body)

object CMRequest {
  implicit val cmRequestEncoder: Encoder[CMRequest] = deriveEncoder
  implicit val cmRequestDecoder: Decoder[CMRequest] = deriveDecoder
}
