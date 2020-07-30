package org.constellation.cmhotwallet.model

object CliMethod extends Enumeration {
  type CliMethod = Value

  val ShowTransfers, ShowPublicKey, GenerateKeyPair, PayTransfer = Value
}
