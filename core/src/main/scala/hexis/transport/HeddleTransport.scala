package hexis.transport

import heddle.BytesLength
import heddle.client.Client
import heddle.http.{Body, Method, Request, Response, Url}
import heddle.http.header.Headers
import hexis.config.Config
import hexis.error.JevError
import _root_.hexis.internal.Platform
import zio.{IO, ZIO, ZLayer}

final class HeddleTransport(client: Client) extends Transport:
  def execute(req: HttpRequest): IO[TransportError, HttpResponse] =
    toHeddle(req).flatMap { request =>
      client
        .batched(request)
        .mapError(mapThrowable)
        .timeoutFail(TransportError.TimedOut)(req.timeout)
        .flatMap(fromHeddle)
    }

object HeddleTransport:
  val layer: ZLayer[Config, Nothing, Transport] =
    ZLayer.fromZIO(
      ZIO.service[Config].flatMap { cfg =>
        clientConfig(cfg).foldZIO(
          err => ZIO.dieMessage(err.message),
          ZIO.succeed,
        )
      }
    ) >>> Client.layer >>> ZLayer.fromFunction(HeddleTransport(_))

  private def clientConfig(cfg: Config): IO[JevError, Client.Config] =
    if !cfg.allowBrowser && Platform.isBrowser then ZIO.fail(JevError.BrowserForbidden)
    else ZIO.succeed(toClientConfig(cfg))

  def layerFrom(client: Client): ZLayer[Config, Nothing, Transport] =
    ZLayer.succeed(HeddleTransport(client))

  def toClientConfig(cfg: Config): Client.Config =
    val h = cfg.heddle
    Client.Config(
      maxConnectionsPerHost = h.maxConnectionsPerHost,
      maxIdlePerHost = h.maxIdlePerHost,
      connectTimeout = h.connectTimeout,
      idleTimeout = h.idleTimeout,
      poolIdleTimeout = h.poolIdleTimeout,
      addUserAgent = false,
      maxHeaderBytes = BytesLength(h.maxHeaderBytes),
      maxBodyBytes = BytesLength(h.maxBodyBytes),
    )
  end toClientConfig
end HeddleTransport

private def toHeddle(req: HttpRequest): IO[TransportError, Request] =
  ZIO
    .attempt(Url.parse(req.url))
    .mapError(_ => TransportError.InvalidUrl(req.url))
    .map { url =>
      val method = req.method match
        case HttpMethod.Get  => Method.GET
        case HttpMethod.Post => Method.POST
      val headers = req.headers.foldLeft(Headers.empty) { (acc, kv) =>
        acc.add(kv._1, kv._2)
      }
      val body = req.body.fold(Body.empty)(Body.json)
      Request(method, url, headers, body)
    }

private def fromHeddle(res: Response): IO[TransportError, HttpResponse] =
  res.body.utf8.mapError(mapThrowable).map { text =>
    val headers = res.headers.toChunk.map(h => h.name.toString.toLowerCase -> h.value).toMap
    HttpResponse(res.status.code, headers, text)
  }

private def mapThrowable(t: Throwable): TransportError =
  val msg = Option(t.getMessage).getOrElse(t.toString).toLowerCase
  if msg.contains("timed out") || msg.contains("timeout") then TransportError.TimedOut
  else TransportError.Unreachable(Option(t.getMessage).getOrElse(t.toString))
