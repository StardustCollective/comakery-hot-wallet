package org.constellation.cmhotwallet

import java.security.KeyPair

import pureconfig._
import pureconfig.generic.auto._
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import org.constellation.cmhotwallet.model._
import org.constellation.cmhotwallet.model.config.{CliConfig, Config, ProjectConfig}
import org.constellation.keytool.KeyStoreUtils
import pureconfig.ConfigSource
import scopt.OParser

import scala.util.Try

class Loader() {

  val CL_STOREPASS = "CL_STOREPASS"
  val CL_KEYPASS = "CL_KEYPASS"

  val COMAKERY_PROJECT_API_TRANSACTION_KEY = "COMAKERY_PROJECT_API_TRANSACTION_KEY"
  val COMAKERY_PROJECT_ID = "COMAKERY_PROJECT_ID"

  def loadConfig[F[_]: Sync](): EitherT[F, Throwable, Config] =
    EitherT.fromEither(
      ConfigSource.default
        .load[Config]
        .leftMap(e => new RuntimeException(e.toString))
    )

  def loadCliParams[F[_]: Sync](args: List[String]): EitherT[F, Throwable, CliConfig] = {
    val builder = OParser.builder[CliConfig]

    val cliParser = {
      import builder._
      OParser.sequence(
        programName("cl-comakery-hot-wallet"),
        opt[Unit]("env_args").optional
          .abbr("e")
          .action((_, c) => c.copy(loadFromEnvArgs = true)),
        cmd("pay-transfer")
          .action((_, c) => c.copy(method = CliMethod.PayTransfer))
          .text("pay-transfer")
          .children(
            opt[String]("keystore").required
              .action((x, c) => c.copy(keystore = x)),
            opt[String]("alias").required
              .action((x, c) => c.copy(alias = x)),
            if (args.contains("-e") || args.contains("--env_args"))
              OParser.sequence(
                opt[String]("storepass").optional
                  .action((x, c) => c.copy(storepass = x.toCharArray)),
                opt[String]("keypass").optional
                  .action((x, c) => c.copy(keypass = x.toCharArray))
              )
            else
              OParser.sequence(
                opt[String]("storepass").required
                  .action((x, c) => c.copy(storepass = x.toCharArray)),
                opt[String]("keypass").required
                  .action((x, c) => c.copy(keypass = x.toCharArray))
              ),
            opt[Long]("transferId").optional
              .valueName("<long>")
              .validate(x => if (x > 0L) success else failure("transferId must be > 0"))
              .action((x, c) => c.copy(transferId = x.some)),
            opt[String]("prevTx").required
              .valueName("<file>")
              .abbr("p")
              .action((x, c) => c.copy(prevTxPath = x)),
            opt[String]("txFile").required
              .valueName("<file>")
              .abbr("f")
              .action((x, c) => c.copy(txPath = x))
          )
      )
    }

    EitherT.fromEither[F] {
      OParser
        .parse(cliParser, args, config.CliConfig())
        .toRight(new RuntimeException("CLI params are missing"))
    }
  }

  def loadEnvPasswords[F[_]: Sync](): EitherT[F, Throwable, EnvPasswords] =
    EitherT.fromEither {
      for {
        storepass <- sys.env
          .get(CL_STOREPASS)
          .toRight(new RuntimeException(s"$CL_STOREPASS environment variable is missing"))
        keypass <- sys.env
          .get(CL_KEYPASS)
          .toRight(new RuntimeException(s"$CL_KEYPASS environment variable is missing"))
      } yield EnvPasswords(storepass.toCharArray, keypass.toCharArray)
    }

  def getKeyPair[F[_]: Sync](cliParams: CliConfig): EitherT[F, Throwable, KeyPair] =
    if (cliParams.loadFromEnvArgs)
      for {
        passwords <- loadEnvPasswords[F]()
        keyPair <- KeyStoreUtils
          .keyPairFromStorePath(cliParams.keystore, cliParams.alias, passwords.storepass, passwords.keypass)
      } yield keyPair
    else
      KeyStoreUtils
        .keyPairFromStorePath(cliParams.keystore, cliParams.alias, cliParams.storepass, cliParams.keypass)


  def loadProjectConfig[F[_]: Sync](): EitherT[F, Throwable, ProjectConfig] =
    EitherT.fromEither {
      for {
        idStr <- sys.env
          .get(COMAKERY_PROJECT_ID)
          .toRight(new RuntimeException(s"$COMAKERY_PROJECT_ID environment variable is missing."))
        id <- Try(idStr.toLong).toOption
          .toRight(new RuntimeException(s"$COMAKERY_PROJECT_ID environment variable needs to be a Number."))
        apiTransactionKey <- sys.env
          .get(COMAKERY_PROJECT_API_TRANSACTION_KEY)
          .toRight(new RuntimeException(s"$COMAKERY_PROJECT_API_TRANSACTION_KEY environment variable is missing."))
      } yield ProjectConfig(id = id, apiTransactionKey = apiTransactionKey)
    }
}

object Loader {
  def apply() = new Loader()
}
