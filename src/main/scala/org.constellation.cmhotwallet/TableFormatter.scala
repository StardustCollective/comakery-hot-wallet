package org.constellation.cmhotwallet

import org.constellation.cmhotwallet.model.Transfer
import scalax.cli.Table
import shapeless.Sized

object TableFormatter {

  def fromTransfers(transfers: List[Transfer]) = {
    val table = Table(
      Sized(
        "id",
        "transferTypeId",
        "amount",
        "quantity",
        "totalAmount",
        "description",
        "ethTxAddress",
        "ethTxError",
        "status",
        "createdAt",
        "updatedAt",
        "accountId"
      )
    )

    transfers.foreach(
      t =>
        table.rows += Sized(
          t.id.toString,
          t.transferTypeId.toString,
          t.amount.toString,
          t.quantity.toString,
          t.totalAmount.toString,
          t.description,
          t.ethereumTransactionAddress.toString,
          t.ethereumTransactionError.toString,
          t.status,
          t.createdAt,
          t.updatedAt,
          t.accountId.toString
      )
    )

    table.alignments(1) = Table.Alignment.Right
    table
  }
}
