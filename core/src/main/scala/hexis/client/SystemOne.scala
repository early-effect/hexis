package hexis.client

import hexis.ask.Ask
import hexis.config.Config
import hexis.domain.*
import hexis.error.JevError
import hexis.transport.{HttpMethod, HttpRequest, HttpResponse, Transport, TransportError}
import hexis.wire.{WireEvaluateRequest, WireEvaluateResponse, WireModelCard, WireModelsResponse}
import zio.json.*
import zio.json.ast.Json
import zio.{Chunk, Duration, IO, ZIO, ZLayer}

trait SystemOne:
  def evaluate[S, Q, O](state: S, questions: Q, model: Option[Model] = None)(using
      ToState[S],
      Ask.Aux[Q, O],
  ): IO[JevError, O]
  def models: IO[JevError, Chunk[ModelCard]]

object SystemOne:
  def evaluate[S, Q, O](state: S, questions: Q, model: Option[Model] = None)(using
      ToState[S],
      Ask.Aux[Q, O],
  ): ZIO[SystemOne, JevError, O] =
    ZIO.serviceWithZIO(_.evaluate(state, questions, model))

  def models: ZIO[SystemOne, JevError, Chunk[ModelCard]] =
    ZIO.serviceWithZIO(_.models)

  val layer: ZLayer[Transport & Config, Nothing, SystemOne] =
    ZLayer.fromFunction(Live.apply)

  final class Live(transport: Transport, config: Config) extends SystemOne:
    def evaluate[S, Q, O](state: S, questions: Q, model: Option[Model] = None)(using
        toState: ToState[S],
        ask: Ask.Aux[Q, O],
    ): IO[JevError, O] =
      val encoded = ask.encode(questions)
      if encoded.isEmpty then ZIO.fail(JevError.EmptyQuestions)
      else
        for
          st <- ZIO.fromEither(toState.toState(state)).mapError(JevError.InvalidQuestion.apply)
          body = WireEvaluateRequest(st.asJson, model.getOrElse(config.model).render, encoded).toJson
          res    <- request(HttpMethod.Post, "/v1/systemone", Some(body))
          parsed <- decodeSuccess[WireEvaluateResponse](res)
          out    <- ZIO.fromEither(ask.decode(questions, parsed.answers))
        yield out
    end evaluate

    def models: IO[JevError, Chunk[ModelCard]] =
      request(HttpMethod.Get, "/v1/models", None).flatMap(decodeSuccess[WireModelsResponse]).map { r =>
        Chunk.fromIterable(r.models.map(WireModelCard.toDomain))
      }

    private def request(method: HttpMethod, path: String, body: Option[String]): IO[JevError, HttpResponse] =
      val req = HttpRequest(
        method = method,
        url = s"${config.baseUrl}$path",
        headers = defaultHeaders ++ config.headers,
        body = body,
        timeout = config.timeout,
      )
      retrying(transport.execute(req).mapError(mapTransport).flatMap(mapStatus))

    private def defaultHeaders: Map[String, String] =
      Map(
        "Authorization" -> s"Bearer ${config.apiKey.reveal}",
        "Accept"        -> "application/json",
        "Content-Type"  -> "application/json",
        "User-Agent"    -> config.userAgent,
      )

    private def retrying[A](effect: IO[JevError, A]): IO[JevError, A] =
      val r                                                = config.retry
      def loop(n: Int, elapsed: Duration): IO[JevError, A] =
        effect.catchSome {
          case err if retryable(err) && n < r.maxRetries =>
            nextDelay(err, n).flatMap { delay =>
              val spent = Duration.fromNanos(elapsed.toNanos + delay.toNanos)
              if r.budget.exists(b => spent.toNanos > b.toNanos) then ZIO.fail(err)
              else ZIO.sleep(delay) *> loop(n + 1, spent)
            }
        }
      loop(0, Duration.Zero)
    end retrying

    private def nextDelay(err: JevError, n: Int): zio.UIO[Duration] =
      val r          = config.retry
      val retryAfter =
        if r.respectRetryAfter then
          err match
            case JevError.RateLimited(_, after, _) => after
            case JevError.Overloaded(_, after, _)  => after
            case _                                 => None
        else None
      retryAfter match
        case Some(d) =>
          val capNanos = 60L * 1000L * 1000L * 1000L
          ZIO.succeed(if d.toNanos > capNanos then Duration.fromNanos(capNanos) else d)
        case None =>
          val expNanos    = (r.backoffInitial.toNanos.toDouble * math.pow(2.0, n.toDouble)).toLong
          val maxNanos    = r.backoffMax.toNanos
          val cappedNanos = if expNanos > maxNanos then maxNanos else expNanos
          if r.backoffJitter <= 0.0 then ZIO.succeed(Duration.fromNanos(cappedNanos))
          else
            zio.Random
              .nextDoubleBetween(1.0 - r.backoffJitter, 1.0)
              .map(f => Duration.fromNanos((cappedNanos.toDouble * f).toLong))
      end match
    end nextDelay

    private def retryable(err: JevError): Boolean =
      val r = config.retry
      err match
        case JevError.RateLimited(_, _, _) => true
        case JevError.Overloaded(_, _, _)  => true
        case JevError.Server(_, status, _) => r.httpStatuses.contains(status)
        case JevError.Unreachable(_)       => r.retryUnreachable
        case JevError.TimedOut             => r.retryTimeout
        case _                             => false

    private def mapTransport(err: TransportError): JevError =
      err match
        case TransportError.Unreachable(cause) => JevError.Unreachable(cause)
        case TransportError.TimedOut           => JevError.TimedOut
        case TransportError.InvalidUrl(url)    => JevError.Unreachable(s"invalid url: $url")

    private def mapStatus(res: HttpResponse): IO[JevError, HttpResponse] =
      val id = res.headers.get("x-typesafe-request-id").map(RequestId.apply)
      res.status match
        case 200 | 201 | 204 => ZIO.succeed(res)
        case 401             => ZIO.fail(JevError.Unauthorized(id, detailMessage(res.body)))
        case 403             => ZIO.fail(JevError.Forbidden(id, detailMessage(res.body)))
        case 400             =>
          val (kind, msg) = detailObject(res.body)
          ZIO.fail(JevError.BadRequest(id, kind, msg))
        case 422 => ZIO.fail(JevError.Unprocessable(id, detailViolations(res.body)))
        case 429 =>
          ZIO.fail(JevError.RateLimited(id, retryAfter(res), detailMessage(res.body)))
        case 529 =>
          ZIO.fail(JevError.Overloaded(id, retryAfter(res), detailMessage(res.body)))
        case status if status >= 500 =>
          ZIO.fail(JevError.Server(id, status, detailMessage(res.body)))
        case status =>
          ZIO.fail(JevError.Server(id, status, detailMessage(res.body)))
      end match
    end mapStatus

    private def decodeSuccess[A: JsonDecoder](res: HttpResponse): IO[JevError, A] =
      ZIO.fromEither(res.body.fromJson[A]).mapError(err => JevError.Decode("body", err))

    private def retryAfter(res: HttpResponse): Option[Duration] =
      res.headers
        .get("retry-after-ms")
        .flatMap(_.toLongOption)
        .map(Duration.fromMillis)
        .orElse(
          res.headers.get("retry-after").flatMap { raw =>
            raw.toLongOption.map(Duration.fromSeconds)
          }
        )

    private def detailMessage(body: String): String =
      body
        .fromJson[Json]
        .toOption
        .flatMap {
          case obj: Json.Obj =>
            obj.get("detail") match
              case Some(inner: Json.Obj) =>
                inner.get("message").flatMap(_.asString)
              case Some(Json.Str(s)) => Some(s)
              case _                 => None
          case _ => None
        }
        .getOrElse(body)

    private def detailObject(body: String): (String, String) =
      body
        .fromJson[Json]
        .toOption
        .flatMap {
          case obj: Json.Obj =>
            obj.get("detail") match
              case Some(inner: Json.Obj) =>
                Some(
                  (
                    inner.get("error_type").flatMap(_.asString).getOrElse("api_usage_error"),
                    inner.get("message").flatMap(_.asString).getOrElse(body),
                  )
                )
              case _ => None
          case _ => None
        }
        .getOrElse(("api_usage_error", body))

    private def detailViolations(body: String): Chunk[Violation] =
      body
        .fromJson[Json]
        .toOption
        .flatMap {
          case obj: Json.Obj =>
            obj.get("detail") match
              case Some(Json.Arr(items)) =>
                Some(Chunk.fromIterable(items.flatMap(parseViolation)))
              case Some(inner: Json.Obj) =>
                Some(Chunk(Violation(Nil, inner.get("message").flatMap(_.asString).getOrElse(body), "error")))
              case _ => None
          case _ => None
        }
        .getOrElse(Chunk(Violation(Nil, body, "error")))

    private def parseViolation(json: Json): Option[Violation] =
      json match
        case obj: Json.Obj =>
          val path = obj.get("loc") match
            case Some(Json.Arr(xs)) => xs.flatMap(_.asString).toList
            case _                  => Nil
          val msg  = obj.get("msg").flatMap(_.asString).getOrElse("invalid")
          val kind = obj.get("type").flatMap(_.asString).getOrElse("error")
          Some(Violation(path, msg, kind))
        case _ => None
  end Live
end SystemOne
