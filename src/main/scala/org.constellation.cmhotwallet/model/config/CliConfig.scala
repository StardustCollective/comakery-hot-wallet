package org.constellation.cmhotwallet.model.config

import org.constellation.cmhotwallet.model.CliMethod.CliMethod

case class CliConfig(
  method: CliMethod = null,
  cmKeystore: String = null,
  cmAlias: String = null,
  cmStorepass: Array[Char] = null,
  cmKeypass: Array[Char] = null,
  clKeystore: String = null,
  clAlias: String = null,
  clStorepass: Array[Char] = null,
  clKeypass: Array[Char] = null,
  loadFromEnvArgs: Boolean = false,
  transferId: Option[Long] = None,
  pageNr: Int = 1,
  prevTxPath: String = null,
  txPath: String = null
)
