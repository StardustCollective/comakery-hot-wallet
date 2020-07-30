package org.constellation.cmhotwallet.model.request

import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._

sealed trait Transaction

object Transaction {
  implicit val encodeTransaction: Encoder[Transaction] = Encoder.instance {
    case a @ GenerateTransaction(_, _) => a.asJson
    case a @ SubmitTransactionHash(_)  => a.asJson
  }

  implicit val decodeTransaction: Decoder[Transaction] =
    List[Decoder[Transaction]](
      GenerateTransaction.generateTransactionDecoder.widen,
      SubmitTransactionHash.submitTransactionHashDecoder.widen
    ).reduceLeft(_.or(_))
}

final case class GenerateTransaction(
  source: String,
  nonce: String
) extends Transaction

object GenerateTransaction {
  implicit val generateTransactionEncoder: Encoder[GenerateTransaction] = deriveEncoder
  implicit val generateTransactionDecoder: Decoder[GenerateTransaction] = deriveDecoder
}

final case class SubmitTransactionHash(
  txHash: String
) extends Transaction

object SubmitTransactionHash {
  implicit val submitTransactionHashEncoder: Encoder[SubmitTransactionHash] = deriveEncoder
  implicit val submitTransactionHashDecoder: Decoder[SubmitTransactionHash] = deriveDecoder
}
