package org.constellation.cmhotwallet

import java.security.SecureRandom
import java.time.Instant

import cats.data.EitherT
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import io.circe.syntax._
import cats.effect.{ConcurrentEffect, Resource, Sync}
import cats.implicits._
import org.constellation.cmhotwallet.model.{Transaction => CMTransaction}
import org.constellation.cmhotwallet.model.config.{CoMakeryConfig, ProjectConfig}
import org.constellation.cmhotwallet.model.request._
import org.http4s.Method.{POST, PUT}
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.client.Client
import org.http4s.{Header, MalformedMessageBodyFailure, Request, Uri}

class CoMakerClient() {

  import CoMakerClient._

  private val secureRandom: SecureRandom = try {
    SecureRandom.getInstance("NativePRNGNonBlocking")
  } catch {
    case _: Throwable => SecureRandom.getInstanceStrong
  }

  private def nonce: String = BigInt(120, secureRandom).toString(16)

  private def timestamp: String = Instant.now.getEpochSecond.toString

  private def getUri(path: String)(config: CoMakeryConfig): Uri =
    Uri(scheme = Some(Scheme.https), authority = Some(Authority(host = RegName(config.host), port = config.port)))
      .addPath(config.apiSuffix)
      .addPath(path)

  def generateTransaction[F[_] : Sync : ConcurrentEffect](source: String, transferId: Option[Long])(
    config: CoMakeryConfig,
    projectConfig: ProjectConfig
  )(client: Resource[F, Client[F]]): EitherT[F, Throwable, CMTransaction] = {
    val uri = getUri(s"projects/${projectConfig.id}/blockchain_transactions")(config)
    val method = POST
    val body = CMRequest(
      Body(
        data = TransactionData(GenerateTransaction(source, nonce), transferId, None).some,
        url = uri.renderString,
        method = method.toString(),
        nonce = nonce,
        timestamp = timestamp
      )
    )

    val req = Request[F](method = method, uri = uri)
      .withHeaders(
        Header(`Api-Transaction-Key`, projectConfig.apiTransactionKey)
      )
      .withEntity(body)

    RequestRunner
      .run[F, CMTransaction](req)(client)
      .leftMap { case e: MalformedMessageBodyFailure =>
        new Throwable(s"There may be no transfers ready for payment process currently.\nOriginal error: ${e.getMessage()}")
      }
  }

  def submitTransactionHash[F[_] : Sync : ConcurrentEffect](transactionId: Long, txHash: String)(
    config: CoMakeryConfig,
    projectConfig: ProjectConfig
  )(client: Resource[F, Client[F]]): EitherT[F, Throwable, CMTransaction] = {
    val uri = getUri(s"projects/${projectConfig.id}/blockchain_transactions/$transactionId")(config)
    val method = PUT
    val body = CMRequest(
      Body(
        data = TransactionData(SubmitTransactionHash(txHash = txHash), None, None).some,
        url = uri.renderString,
        method = method.toString(),
        nonce = nonce,
        timestamp = timestamp
      )
    )

    val req = Request[F](method = method, uri = uri)
      .withHeaders(
        Header(`Api-Transaction-Key`, projectConfig.apiTransactionKey)
      )
      .withEntity(body)

    RequestRunner.run[F, CMTransaction](req)(client)
  }
}

object CoMakerClient {
  val `Api-Transaction-Key` = "Api-Transaction-Key"

  def apply() = new CoMakerClient()
}
