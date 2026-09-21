package hexis.error

import hexis.domain.{RequestId, Violation}
import zio.{Chunk, Duration}

enum JevError:
  case MissingApiKey
  case BrowserForbidden
  case Unauthorized(id: Option[RequestId], detail: String)
  case Forbidden(id: Option[RequestId], detail: String)
  case BadRequest(id: Option[RequestId], errorType: String, detail: String)
  case Unprocessable(id: Option[RequestId], violations: Chunk[Violation])
  case RateLimited(id: Option[RequestId], retryAfter: Option[Duration], detail: String)
  case Overloaded(id: Option[RequestId], retryAfter: Option[Duration], detail: String)
  case Server(id: Option[RequestId], status: Int, detail: String)
  case Unreachable(cause: String)
  case TimedOut
  case Decode(path: String, detail: String)
  case EmptyQuestions
  case InvalidQuestion(detail: String)

  def requestId: Option[RequestId] =
    this match
      case Unauthorized(id, _)   => id
      case Forbidden(id, _)      => id
      case BadRequest(id, _, _)  => id
      case Unprocessable(id, _)  => id
      case RateLimited(id, _, _) => id
      case Overloaded(id, _, _)  => id
      case Server(id, _, _)      => id
      case _                     => None

  def message: String =
    this match
      case MissingApiKey                    => "API key is missing"
      case BrowserForbidden                 => "Refusing to send an API key from a browser (set Config.allowBrowser)"
      case Unauthorized(_, detail)          => detail
      case Forbidden(_, detail)             => detail
      case BadRequest(_, errorType, detail) => s"$errorType: $detail"
      case Unprocessable(_, violations)     =>
        violations.map(v => s"${v.path.mkString(".")}: ${v.message}").mkString("; ")
      case RateLimited(_, _, detail) => detail
      case Overloaded(_, _, detail)  => detail
      case Server(_, status, detail) => s"$status: $detail"
      case Unreachable(cause)        => cause
      case TimedOut                  => "request timed out"
      case Decode(path, detail)      => s"$path: $detail"
      case EmptyQuestions            => "questions must not be empty"
      case InvalidQuestion(detail)   => detail
end JevError
