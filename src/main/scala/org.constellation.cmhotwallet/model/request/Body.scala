package org.constellation.cmhotwallet.model.request

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

final case class Body(
  data: Option[Data],
  url: String = "",
  method: String = "",
  nonce: String = "",
  timestamp: String = ""
)

object Body {
  implicit val bodyEncoder: Encoder[Body] = deriveEncoder
  implicit val bodyDecoder: Decoder[Body] = deriveDecoder
}
