package org.constellation.cmhotwallet

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, ExitCode, IO, IOApp, Resource, Sync}
import cats.implicits._
import io.chrisdavenport.log4cats.Logger
import io.chrisdavenport.log4cats.slf4j.Slf4jLogger
import org.http4s.client.Client
import org.http4s.client.blaze.BlazeClientBuilder
import org.constellation.cmhotwallet.model.CliMethod
import org.constellation.cmhotwallet.model.config.{CliConfig, Config, ProjectConfig}
import org.constellation.keytool.KeyUtils
import org.constellation.wallet.Wallet.{createTransaction, storeTransaction}
import org.constellation.wallet.{CliConfig => WalletCliConfig}

import scala.io.AnsiColor._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.StdIn

object App extends IOApp {

  val loader = Loader()
  lazy val coMakeryClient = CoMakerClient()
  lazy val constellationClient = ConstellationClient()
  implicit val logger = Slf4jLogger.getLogger[IO]

  def run(args: List[String]): IO[ExitCode] = {
    for {
      config <- loader.loadConfig[IO]()
      projectConfig <- loader.loadProjectConfig[IO]()
      cliParams <- loader.loadCliParams[IO](args)
      client = setupClient[IO]
      _ <- runMethod[IO](config, projectConfig, cliParams)(client)
    } yield ()
  }
    .foldF[ExitCode](
      e => logger.error(s"${RED}Failure:\n${RESET}" + e.toString).map(_ => throw e),
      _ => logger.info("Success! Shutting down.").map(_ => ExitCode.Success)
    )

  def runMethod[F[_]: ConcurrentEffect: Logger](config: Config, projectConfig: ProjectConfig, cliParams: CliConfig)(
    client: Resource[F, Client[F]]
  ): EitherT[F, Throwable, Unit] = {

    cliParams.method match {
      case CliMethod.PayTransfer     => payTransfer(config, projectConfig, cliParams)(client)
      case _                         => EitherT.leftT[F, Unit](new RuntimeException("Unknown command"))
    }
  }

  private def setupClient[F[_]: ConcurrentEffect]: Resource[F, Client[F]] =
    BlazeClientBuilder(global)
      .withConnectTimeout(30.seconds)
      .withMaxTotalConnections(8)
      .withMaxWaitQueueLimit(4)
      .resource

  def payTransfer[F[_]: Logger](config: Config, projectConfig: ProjectConfig, cliParams: CliConfig)(
    client: Resource[F, Client[F]]
  )(implicit F: Sync[F]) =
    for {
      keyPair <- loader.getKeyPair(cliParams)
      sourceAddress = KeyUtils.publicKeyToAddressString(keyPair.getPublic)
      transferId = cliParams.transferId
      cmTransaction <- coMakeryClient.generateTransaction(sourceAddress, transferId)(config.comakery, projectConfig)(client)
      _ <- {
        for {
          _ <- Logger[F].info("You are about to send:")
          _ <- Logger[F].info(s"${CYAN}${cmTransaction.amount}${RESET} DAG to ${YELLOW}${cmTransaction.destination}${RESET}")
          _ <- Logger[F].info("Do you confirm? Only 'yes' will confirm the transaction.")
          input = StdIn.readLine()
          _ <- Logger[F].info(s"Answer: $input")
          _ <-
            if (input != "yes")
              Logger[F].info(s"Operation ${RED}NOT${RESET} confirmed. ${RED}Cancelling${RESET}.") >>
                F.delay(throw new Throwable("Transaction cancelled!"))
            else
              ().pure[F]
        } yield ()
      }.attemptT
      walletCliConfig = WalletCliConfig(
        destination = cmTransaction.destination,
        prevTxPath = cliParams.prevTxPath,
        txPath = cliParams.txPath,
        amount = cmTransaction.amount,
        normalized = true
      )
      clTransaction <- createTransaction(walletCliConfig, keyPair)
      _ <- storeTransaction(walletCliConfig, clTransaction)
      clTxHash <- constellationClient.submitTransaction(clTransaction)(config.constellation.loadBalancer, client)
      _ <- coMakeryClient.submitTransactionHash(cmTransaction.id, clTxHash.value)(config.comakery, projectConfig)(client)
      _ <- Logger[F].info(s"${GREEN}Transaction submitted successfully!\n${RESET}hash: ${YELLOW}${clTxHash.value}${RESET}")
        .attemptT
    } yield ()
}
