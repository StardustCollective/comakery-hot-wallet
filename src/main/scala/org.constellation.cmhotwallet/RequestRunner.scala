package org.constellation.cmhotwallet

import cats.data.EitherT
import cats.effect.{Resource, Sync}
import cats.implicits._
import org.http4s.{EntityDecoder, Request}
import org.http4s.client.Client

object RequestRunner {
  def run[F[_]: Sync, A](
    request: Request[F]
  )(client: Resource[F, Client[F]])(implicit d: EntityDecoder[F, A]): EitherT[F, Throwable, A] =
    client.use { c =>
      c.expect[A](request)
    }.attemptT
}
