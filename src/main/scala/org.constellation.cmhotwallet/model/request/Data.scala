package org.constellation.cmhotwallet.model.request

import cats.implicits._
import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}
import io.circe.syntax._

sealed trait Data

object Data {
  implicit val encodeData: Encoder[Data] = Encoder.instance {
    case a @ TransactionData(_, _, _) => a.asJson
  }

  implicit val decodeData: Decoder[Data] =
    List[Decoder[Data]](
      TransactionData.transactionDataDecoder.widen
    ).reduceLeft(_.or(_))
}

final case class TransactionData(
  transaction: Transaction,
  blockchainTransactableId: Option[Long],
  blockchainTransactableType: Option[String]
) extends Data

object TransactionData {
  implicit val transactionDataEncoder: Encoder[TransactionData] = deriveEncoder
  implicit val transactionDataDecoder: Decoder[TransactionData] = deriveDecoder
}
