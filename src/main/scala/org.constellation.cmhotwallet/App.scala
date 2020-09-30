package org.constellation.cmhotwallet

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp, Resource, Sync}
import cats.implicits._
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.constellation.cmhotwallet.CoMakerClient._
import org.constellation.cmhotwallet.model.CliMethod
import org.constellation.cmhotwallet.Ed25519KeyTool.publicKeyToBase64
import org.constellation.cmhotwallet.model.config.{CliConfig, CoMakeryConfig, Config}
import org.constellation.keytool.KeyUtils
import org.constellation.wallet.Wallet.{createTransaction, storeTransaction}
import org.constellation.wallet.{CliConfig => WalletCliConfig}

import scala.io.AnsiColor._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

object App extends IOApp {

  val keyTool = Ed25519KeyTool()
  val loader = Loader(keyTool)
  lazy val coMakeryClient = CoMakerClient(keyTool)
  lazy val constellationClient = ConstellationClient()

  def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- loader.loadConfig[IO]()
      cliParams <- loader.loadCliParams[IO](args)
      client = setupClient[IO]
      _ <- runMethod[IO](config, cliParams)(client)
    } yield ()
  }.fold[ExitCode](throw _, _ => ExitCode.Success)

  def runMethod[F[_]: Sync: ConcurrentEffect](config: Config, cliParams: CliConfig)(
    client: Resource[F, Client[F]]
  ): EitherT[F, Throwable, Unit] =
    cliParams.method match {
      case CliMethod.GenerateKeyPair => generateKeyPair(cliParams)
      case CliMethod.ShowPublicKey   => showPublicKey(cliParams)
      case CliMethod.ShowTransfers   => showTransfers(config.comakery, cliParams)(client)
      case CliMethod.PayTransfer     => payTransfer(config, cliParams)(client)
      case _                         => EitherT.leftT[F, Unit](new RuntimeException("Unknown command"))
    }

  private def setupClient[F[_]: Sync: ConcurrentEffect]: Resource[F, Client[F]] =
    BlazeClientBuilder(global)
      .withConnectTimeout(30.seconds)
      .withMaxTotalConnections(8)
      .withMaxWaitQueueLimit(4)
      .resource

  def generateKeyPair[F[_]: Sync](cliParams: CliConfig): EitherT[F, Throwable, Unit] = {
    val updatedParams =
      if (cliParams.loadFromEnvArgs)
        loader.loadCMEnvPasswords()
          .map(ep => cliParams.copy(cmStorepass = ep.storepass, cmKeypass = ep.keypass))
      else
        cliParams.pure[F].attemptT

    for {
      params <- updatedParams
      _ <- keyTool.generateAndStoreKeyPair(params.cmKeystore, params.cmAlias, params.cmStorepass, params.cmKeypass)
      _ <- println(s"${GREEN}KeyPair created successfully!${RESET}").pure[F].attemptT
    } yield ()
  }

  def showPublicKey[F[_]](cliParams: CliConfig)(implicit F: Sync[F]): EitherT[F, Throwable, Unit] =
    for {
      keyPair <- loader.getCMKeyPair(cliParams)
      publicKey = publicKeyToBase64(keyPair.value.getPublic)
      _ <- println(s"${GREEN}${publicKey}${RESET}").pure[F].attemptT
    } yield ()

  def showTransfers[F[_]](config: CoMakeryConfig, cliParams: CliConfig)(
    client: Resource[F, Client[F]]
  )(implicit F: Sync[F], C: ConcurrentEffect[F]) =
    for {
      keyPair <- loader.getCMKeyPair(cliParams)
      response <- coMakeryClient.getTransfers(config, cliParams)(keyPair, client)
      (headers, transfers) = response
      table = TableFormatter.fromTransfers(transfers.sortBy(_.id))
      _ <- F.delay {
        println(
          s"Page: ${cliParams.pageNr}, ${headers.get(Total).getOrElse("unknown")}, ${headers.get(`Per-Page`).getOrElse("unknown")}"
        )
        table.print()
      }.attemptT
    } yield ()

  def payTransfer[F[_]](config: Config, cliParams: CliConfig)(
    client: Resource[F, Client[F]]
  )(implicit F: Sync[F], C: ConcurrentEffect[F]) =
    for {
      cmKeyPair <- loader.getCMKeyPair(cliParams)
      clKeyPair <- loader.getCLKeyPair(cliParams)
      sourceAddress = KeyUtils.publicKeyToAddressString(clKeyPair.value.getPublic)
      transferId = cliParams.transferId
      cmTransaction <- coMakeryClient.generateTransaction(sourceAddress, transferId)(config.comakery)(cmKeyPair, client)
      walletCliConfig = WalletCliConfig(
        destination = cmTransaction.destination,
        prevTxPath = cliParams.prevTxPath,
        txPath = cliParams.txPath,
        amount = cmTransaction.amount,
        normalized = true
      )
      clTransaction <- createTransaction(walletCliConfig, clKeyPair.value)
      _ <- storeTransaction(walletCliConfig, clTransaction)
      clTxHash <- constellationClient.submitTransaction(clTransaction)(config.constellation.loadBalancer, client)
      _ <- coMakeryClient.submitTransactionHash(cmTransaction.id, clTxHash)(config.comakery)(cmKeyPair, client)
      _ <- println(s"${GREEN}Transaction submitted successfully!\n${RESET}hash: ${CYAN}$clTxHash${RESET}")
        .pure[F]
        .attemptT
    } yield ()
}
