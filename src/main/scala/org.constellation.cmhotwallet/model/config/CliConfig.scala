package org.constellation.cmhotwallet.model.config

import org.constellation.cmhotwallet.model.CliMethod.CliMethod

case class CliConfig(
  method: CliMethod = null,
  keystore: String = null,
  alias: String = null,
  storepass: Array[Char] = null,
  keypass: Array[Char] = null,
  loadFromEnvArgs: Boolean = false,
  transferId: Option[Long] = None,
  prevTxPath: String = null,
  txPath: String = null
)
