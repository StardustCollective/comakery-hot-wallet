package org.constellation.cmhotwallet.model

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

case class Transaction(
  id: Long,
  blockchainTransactableId: Long,
  destination: String,
  source: String,
  amount: Long,
  nonce: Long,
  contractAddress: Option[String],
  network: Option[String],
  txHash: Option[String],
  txRaw: Option[String],
  status: String,
  statusMessage: Option[String],
  createdAt: String,
  updatedAt: String,
  syncedAt: Option[String]
)

object Transaction {
  implicit val transactionEncoder: Encoder[Transaction] = deriveEncoder
  implicit val transactionDecoder: Decoder[Transaction] = deriveDecoder
}
