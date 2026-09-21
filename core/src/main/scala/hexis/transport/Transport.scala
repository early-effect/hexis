package hexis.transport

import hexis.config.Config
import zio.{Chunk, Duration, IO, Ref, ULayer, ZIO, ZLayer}

enum HttpMethod:
  case Get, Post

  def render: String =
    this match
      case Get  => "GET"
      case Post => "POST"

final case class HttpRequest(
    method: HttpMethod,
    url: String,
    headers: Map[String, String],
    body: Option[String],
    timeout: Duration,
)

final case class HttpResponse(
    status: Int,
    headers: Map[String, String],
    body: String,
)

enum TransportError:
  case Unreachable(cause: String)
  case TimedOut
  case InvalidUrl(url: String)

trait Transport:
  def execute(req: HttpRequest): IO[TransportError, HttpResponse]

object Transport:
  def execute(req: HttpRequest): ZIO[Transport, TransportError, HttpResponse] =
    ZIO.serviceWithZIO(_.execute(req))

  val live: ZLayer[Config, Nothing, Transport] = HeddleTransport.layer

  def test(script: TestTransport.Script): ULayer[Transport] =
    ZLayer.fromZIO(TestTransport.make(script))

  def test(respond: HttpRequest => HttpResponse | TransportError): ULayer[Transport] =
    ZLayer.succeed(TestTransport.Respond(respond))

  def heddle(client: _root_.heddle.client.Client): ZLayer[Config, Nothing, Transport] =
    HeddleTransport.layerFrom(client)
end Transport

object TestTransport:
  final case class Script(responses: Chunk[HttpResponse | TransportError]):
    def ++(that: Script): Script = Script(responses ++ that.responses)

  object Script:
    val empty: Script = Script(Chunk.empty)

    def apply(first: HttpResponse | TransportError, rest: (HttpResponse | TransportError)*): Script =
      Script(Chunk(first) ++ Chunk.fromIterable(rest))

  final class Live(remaining: Ref[Chunk[HttpResponse | TransportError]], seen: Ref[Chunk[HttpRequest]])
      extends Transport:
    def execute(req: HttpRequest): IO[TransportError, HttpResponse] =
      seen.update(_ :+ req) *>
        remaining
          .modify { rs =>
            rs.headOption match
              case Some(head) => (head, rs.drop(1))
              case None       => (TransportError.Unreachable("TestTransport exhausted"), Chunk.empty)
          }
          .flatMap {
            case e: TransportError => ZIO.fail(e)
            case r: HttpResponse   => ZIO.succeed(r)
          }

    def requests: ULayer[Chunk[HttpRequest]] = ZLayer.fromZIO(seen.get)
  end Live

  def make(script: Script): zio.UIO[Live] =
    for
      rem  <- Ref.make(script.responses)
      seen <- Ref.make(Chunk.empty[HttpRequest])
    yield Live(rem, seen)

  final class Respond(f: HttpRequest => HttpResponse | TransportError) extends Transport:
    def execute(req: HttpRequest): IO[TransportError, HttpResponse] =
      f(req) match
        case e: TransportError => ZIO.fail(e)
        case r: HttpResponse   => ZIO.succeed(r)
end TestTransport
