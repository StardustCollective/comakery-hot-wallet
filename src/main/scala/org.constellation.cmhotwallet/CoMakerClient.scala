package org.constellation.cmhotwallet

import java.security.SecureRandom
import java.time.Instant

import cats.data.EitherT
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import io.circe.syntax._
import cats.effect.{ConcurrentEffect, Resource, Sync}
import cats.implicits._
import org.constellation.cmhotwallet.model.{CMKeyPair, Transfer, Transaction => CMTransaction}
import org.constellation.cmhotwallet.model.config.{CliConfig, CoMakeryConfig}
import org.constellation.cmhotwallet.Ed25519KeyTool.publicKeyToBase64
import org.constellation.cmhotwallet.model.request._
import org.http4s.Method.{GET, POST, PUT}
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.client.Client
import org.http4s.{Header, Headers, MalformedMessageBodyFailure, Request, Uri}
import org.http4s.util.CaseInsensitiveString

class CoMakerClient(keyTool: Ed25519KeyTool) {
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

  def getTransfers[F[_]: Sync: ConcurrentEffect](
    config: CoMakeryConfig,
    cliParams: CliConfig
  )(keyPair: CMKeyPair, client: Resource[F, Client[F]]): EitherT[F, Throwable, (Headers, List[Transfer])] = {
    val uri = getUri(s"projects/${config.projectId}/transfers")(config)
      .withQueryParam(Page, cliParams.pageNr)
    val method = GET
    val body =
      Body(
        data = None,
        url = uri.removeQueryParam(Page).renderString,
        method = method.toString(),
        nonce = nonce,
        timestamp = timestamp
      )
    val signedBody = signBody(body)(keyPair)

    val req = Request[F](method = method, uri = uri)
      .withHeaders(Header(`API-Key`, config.constellationApiKey))
      .withEntity(signedBody)

    RequestRunner.run[F, Headers, List[Transfer]](
      req,
      resp =>
        resp
          .as[List[Transfer]]
          .map((resp.headers, _))
    )(client)
  }

  def generateTransaction[F[_]: Sync: ConcurrentEffect](source: String, transferId: Option[Long])(
    config: CoMakeryConfig
  )(keyPair: CMKeyPair, client: Resource[F, Client[F]]): EitherT[F, Throwable, CMTransaction] = {
    val uri = getUri(s"projects/${config.projectId}/blockchain_transactions")(config)
    val method = POST
    val body = Body(
      data = TransactionData(GenerateTransaction(source, nonce), transferId, None).some,
      url = uri.renderString,
      method = method.toString(),
      nonce = nonce,
      timestamp = timestamp
    )
    val signedBody = signBody(body)(keyPair)

    val req = Request[F](method = method, uri = uri)
      .withHeaders(
        Header(`Api-Transaction-Key`, config.constellationApiKey)
      )
      .withEntity(signedBody)

    RequestRunner
      .run[F, CMTransaction](req)(client)
      .leftMap{ case _: MalformedMessageBodyFailure => new Throwable("There are no transfers ready for payment process currently.") }
  }

  def submitTransactionHash[F[_]: Sync: ConcurrentEffect](transactionId: Long, txHash: String)(
    config: CoMakeryConfig
  )(keyPair: CMKeyPair, client: Resource[F, Client[F]]): EitherT[F, Throwable, CMTransaction] = {
    val uri = getUri(s"projects/${config.projectId}/blockchain_transactions/$transactionId")(config)
    val method = PUT
    val body = Body(
      data = TransactionData(SubmitTransactionHash(txHash = txHash), None, None).some,
      url = uri.renderString,
      method = method.toString(),
      nonce = nonce,
      timestamp = timestamp
    )
    val signedBody = signBody(body)(keyPair)

    val req = Request[F](method = method, uri = uri)
      .withHeaders(
        Header(`Api-Transaction-Key`, config.constellationApiKey)
      )
      .withEntity(signedBody)

    RequestRunner.run[F, CMTransaction](req)(client)
  }

  private def signBody(body: Body)(keyPair: CMKeyPair): CMRequest = {
    val toSign = body.asJson.noSpacesSortKeys
    val signature = keyTool.sign(toSign)(keyPair.value.getPrivate)

    CMRequest(
      body,
      Proof(
        signature = signature,
        `type` = PROOF_TYPE,
        verificationMethod = publicKeyToBase64(keyPair.value.getPublic)
      )
    )
  }
}

object CoMakerClient {
  val PROOF_TYPE = "Ed25519Signature2018"
  val `API-Key` = "API-Key"
  val `Api-Transaction-Key` = "Api-Transaction-Key"
  val Total = CaseInsensitiveString("Total")
  val `Per-Page` = CaseInsensitiveString("Per-Page")
  val Page = "page"

  def apply(keyTool: Ed25519KeyTool) = new CoMakerClient(keyTool)
}
