package org.constellation.cmhotwallet

import cats.data.EitherT
import org.http4s.circe.CirceEntityEncoder._
import org.http4s.circe.CirceEntityDecoder._
import cats.effect.{Resource, Sync}
import cats.implicits._
import org.constellation.cmhotwallet.model.CLTransactionHash
import org.constellation.cmhotwallet.model.config.LoadBalancerConfig
import org.constellation.cmhotwallet.model.request.CLTransaction._
import org.constellation.wallet.{Transaction => CLTransaction}
import org.http4s.Method.POST
import org.http4s.{Request, Uri}
import org.http4s.Uri.{Authority, RegName, Scheme}
import org.http4s.client.Client

class ConstellationClient() {
  private def getUri(path: String)(config: LoadBalancerConfig): Uri =
    Uri(
      scheme = Some(Scheme.http),
      authority = Some(Authority(host = RegName(config.host), port = config.port.some))
    ).addPath(path)

  def submitTransaction[F[_]: Sync](
    tx: CLTransaction
  )(config: LoadBalancerConfig, client: Resource[F, Client[F]]): EitherT[F, Throwable, CLTransactionHash] = {
    val uri = getUri("transaction")(config)

    val req = Request[F](method = POST, uri = uri)
      .withEntity(tx)

    RequestRunner.run[F, CLTransactionHash](req)(client)
  }
}

object ConstellationClient {
  def apply() = new ConstellationClient()
}
