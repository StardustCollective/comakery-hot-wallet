package org.constellation.cmhotwallet.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Transfer(
  id: Int,
  transferTypeId: Int,
  amount: Double,
  quantity: Double,
  totalAmount: Double,
  description: String,
  ethereumTransactionAddress: Option[String],
  ethereumTransactionError: Option[String],
  status: String,
  createdAt: String,
  updatedAt: String,
  accountId: Option[Int]
)

object Transfer {
  implicit val transferEncoder: Encoder[Transfer] = deriveEncoder
  implicit val transferDecoder: Decoder[Transfer] = deriveDecoder
}
