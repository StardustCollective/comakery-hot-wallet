package org.constellation.cmhotwallet

import pureconfig._
import pureconfig.generic.auto._
import cats.data.EitherT
import cats.effect.Sync
import cats.implicits._
import org.constellation.cmhotwallet.model._
import org.constellation.cmhotwallet.model.config.{CliConfig, Config}
import org.constellation.keytool.KeyStoreUtils
import pureconfig.ConfigSource
import scopt.OParser

class Loader(keyTool: Ed25519KeyTool) {

  val CM_STOREPASS = "CM_STOREPASS"
  val CM_KEYPASS = "CM_KEYPASS"
  val CL_STOREPASS = "CL_STOREPASS"
  val CL_KEYPASS = "CL_KEYPASS"

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
        opt[String]("cmKeystore").required
          .action((x, c) => c.copy(cmKeystore = x)),
        opt[String]("cmAlias").required
          .action((x, c) => c.copy(cmAlias = x)),
        if (args.contains("-e") || args.contains("--env_args"))
          OParser.sequence(
            opt[String]("cmStorepass").optional
              .action((x, c) => c.copy(cmStorepass = x.toCharArray)),
            opt[String]("cmKeypass").optional
              .action((x, c) => c.copy(cmKeypass = x.toCharArray))
          )
        else
          OParser.sequence(
            opt[String]("cmStorepass").required
              .action((x, c) => c.copy(cmStorepass = x.toCharArray)),
            opt[String]("cmKeypass").required
              .action((x, c) => c.copy(cmKeypass = x.toCharArray))
          ),
        opt[Unit]("env_args").optional
          .abbr("e")
          .action((_, c) => c.copy(loadFromEnvArgs = true)),
        cmd("generate-keypair")
          .action((_, c) => c.copy(method = CliMethod.GenerateKeyPair))
          .text("generate-keypair"),
        cmd("show-publickey")
          .action((_, c) => c.copy(method = CliMethod.ShowPublicKey))
          .text("show-publickey"),
        cmd("show-transfers")
          .action((_, c) => c.copy(method = CliMethod.ShowTransfers))
          .text("show-transfers")
          .children(
            opt[Int]("pageNr").optional
              .action((x, c) => c.copy(pageNr = x))
          ),
        cmd("pay-transfer")
          .action((_, c) => c.copy(method = CliMethod.PayTransfer))
          .text("pay-transfer")
          .children(
            opt[String]("clKeystore").required
              .action((x, c) => c.copy(clKeystore = x)),
            opt[String]("clAlias").required
              .action((x, c) => c.copy(clAlias = x)),
            if (args.contains("-e") || args.contains("--env_args"))
              OParser.sequence(
                opt[String]("clStorepass").optional
                  .action((x, c) => c.copy(clStorepass = x.toCharArray)),
                opt[String]("clKeypass").optional
                  .action((x, c) => c.copy(clKeypass = x.toCharArray))
              )
            else
              OParser.sequence(
                opt[String]("clStorepass").required
                  .action((x, c) => c.copy(clStorepass = x.toCharArray)),
                opt[String]("clKeypass").required
                  .action((x, c) => c.copy(clKeypass = x.toCharArray))
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

  def loadCMEnvPasswords[F[_]: Sync](): EitherT[F, Throwable, CMEnvPasswords] =
    EitherT.fromEither {
      for {
        storepass <- sys.env
          .get(CM_STOREPASS)
          .toRight(new RuntimeException(s"$CM_STOREPASS environment variable is missing"))
        keypass <- sys.env
          .get(CM_KEYPASS)
          .toRight(new RuntimeException(s"$CM_KEYPASS environment variable is missing"))
      } yield CMEnvPasswords(storepass.toCharArray, keypass.toCharArray)
    }

  def loadCLEnvPasswords[F[_]: Sync](): EitherT[F, Throwable, CLEnvPasswords] =
    EitherT.fromEither {
      for {
        storepass <- sys.env
          .get(CL_STOREPASS)
          .toRight(new RuntimeException(s"$CL_STOREPASS environment variable is missing"))
        keypass <- sys.env
          .get(CL_KEYPASS)
          .toRight(new RuntimeException(s"$CL_KEYPASS environment variable is missing"))
      } yield CLEnvPasswords(storepass.toCharArray, keypass.toCharArray)
    }

  def getCMKeyPair[F[_]: Sync](cliParams: CliConfig): EitherT[F, Throwable, CMKeyPair] =
    if (cliParams.loadFromEnvArgs)
      for {
        passwords <- loadCMEnvPasswords[F]()
        keyPair <-
          keyTool.openKeyStoreAndGetKeyPair(
            cliParams.cmKeystore,
            cliParams.cmAlias,
            passwords.storepass,
            passwords.keypass
          )
      } yield CMKeyPair(keyPair)
    else
      keyTool.openKeyStoreAndGetKeyPair(
          cliParams.cmKeystore,
          cliParams.cmAlias,
          cliParams.cmStorepass,
          cliParams.cmKeypass
        )
        .map(CMKeyPair)

  def getCLKeyPair[F[_]: Sync](cliParams: CliConfig): EitherT[F, Throwable, CLKeyPair] =
    if (cliParams.loadFromEnvArgs)
      for {
        passwords <- loadCLEnvPasswords[F]()
        keyPair <- KeyStoreUtils
          .keyPairFromStorePath(cliParams.clKeystore, cliParams.clAlias, passwords.storepass, passwords.keypass)
      } yield CLKeyPair(keyPair)
    else
      KeyStoreUtils
        .keyPairFromStorePath(cliParams.clKeystore, cliParams.clAlias, cliParams.clStorepass, cliParams.clKeypass)
        .map(CLKeyPair)
}

object Loader {
  def apply(keyTool: Ed25519KeyTool) = new Loader(keyTool)
}
