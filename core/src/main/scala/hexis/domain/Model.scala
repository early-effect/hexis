package hexis.domain

enum Model:
  case Latest
  case Preview
  case Pinned(id: String)

  def render: String =
    this match
      case Latest     => "jev-latest"
      case Preview    => "jev-preview"
      case Pinned(id) => id
end Model

object Model:
  def parse(raw: String): Model =
    raw.trim match
      case "jev-latest"  => Latest
      case "jev-preview" => Preview
      case other         => Pinned(other)

final case class ModelCard(name: String, description: String, releaseDate: String)

opaque type ApiKey <: String = String

object ApiKey:
  def apply(value: String): Either[String, ApiKey] =
    val trimmed = value.trim
    if trimmed.isEmpty then Left("API key must be non-empty")
    else Right(trimmed)

  def unsafely(value: String): ApiKey = value.trim

  extension (k: ApiKey)
    def reveal: String   = k
    def redacted: String =
      if k.length <= 8 then "****"
      else s"${k.take(4)}…${k.takeRight(4)}"
end ApiKey

opaque type RequestId <: String = String

object RequestId:
  def apply(value: String): RequestId         = value
  extension (id: RequestId) def value: String = id

final case class Usage(inputTokens: Option[Int], outputTokens: Option[Int])

final case class Violation(path: List[String], message: String, kind: String)
