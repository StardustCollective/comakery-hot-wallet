package org.constellation.cmhotwallet.model.request

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

object CLTransaction {
  import org.constellation.wallet.{
    Edge,
    HashSignature,
    Id,
    LastTransactionRef,
    ObservationEdge,
    SignatureBatch,
    SignedObservationEdge,
    Transaction,
    TransactionEdgeData,
    TypedEdgeHash
  }

  implicit val clLastTransactionRefEncoder: Encoder[LastTransactionRef] = deriveEncoder
  implicit val clLastTransactionRefDecoder: Decoder[LastTransactionRef] = deriveDecoder

  implicit val clTransactionEdgeDataEncoder: Encoder[TransactionEdgeData] = deriveEncoder
  implicit val clTransactionEdgeDataDecoder: Decoder[TransactionEdgeData] = deriveDecoder

  implicit val clTypedEdgeHashEncoder: Encoder[TypedEdgeHash] = deriveEncoder
  implicit val clTypedEdgeHashDecoder: Decoder[TypedEdgeHash] = deriveDecoder

  implicit val clObservationEdgeEncoder: Encoder[ObservationEdge] = deriveEncoder
  implicit val clObservationEdgeDecoder: Decoder[ObservationEdge] = deriveDecoder

  implicit val clSignatureBatchEncoder: Encoder[SignatureBatch] = deriveEncoder
  implicit val clSignatureBatchDecoder: Decoder[SignatureBatch] = deriveDecoder

  implicit val clIdEncoder: Encoder[Id] = deriveEncoder
  implicit val clIdDecoder: Decoder[Id] = deriveDecoder

  implicit val clHashSignatureEncoder: Encoder[HashSignature] = deriveEncoder
  implicit val clHashSignatureDecoder: Decoder[HashSignature] = deriveDecoder

  implicit val clSignedObservationEdgeEncoder: Encoder[SignedObservationEdge] = deriveEncoder
  implicit val clSignedObservationEdgeDecoder: Decoder[SignedObservationEdge] = deriveDecoder

  implicit val clEdgeTransactionDataEncoder: Encoder[Edge[TransactionEdgeData]] = deriveEncoder
  implicit val clEdgeTransactionDataDecoder: Decoder[Edge[TransactionEdgeData]] = deriveDecoder

  implicit val clTransactionEncoder: Encoder[Transaction] = deriveEncoder
  implicit val clTransactionDecoder: Decoder[Transaction] = deriveDecoder
}
