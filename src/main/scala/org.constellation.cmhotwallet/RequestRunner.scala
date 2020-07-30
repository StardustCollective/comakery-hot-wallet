package org.constellation.cmhotwallet

import cats.data.EitherT
import cats.effect.{ConcurrentEffect, Resource, Sync}
import cats.implicits._
import org.http4s.{EntityDecoder, Request, Response}
import org.http4s.client.Client

object RequestRunner {
  def run[F[_]: Sync: ConcurrentEffect, A](
    request: Request[F]
  )(client: Resource[F, Client[F]])(implicit d: EntityDecoder[F, A]): EitherT[F, Throwable, A] =
    client.use { c =>
      c.expect[A](request)
    }.attemptT

  def run[F[_]: Sync: ConcurrentEffect, A, B](request: Request[F], f: Response[F] => F[(A, B)])(
    client: Resource[F, Client[F]]
  )(implicit d: EntityDecoder[F, B]): EitherT[F, Throwable, (A, B)] =
    client.use { c =>
      c.fetch[(A, B)](request)(f)
    }.attemptT
}
