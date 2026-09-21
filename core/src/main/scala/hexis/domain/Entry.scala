package hexis.domain

import zio.Chunk
import zio.json.ast.Json

enum Entry:
  case Text(value: String)
  case Struct(value: Json)
  case Arr(values: Chunk[Entry])
  case Empty

object Entry:
  def text(value: String): Entry = Text(value)

  def fromJson(json: Json): Entry =
    json match
      case Json.Null    => Empty
      case Json.Str(s)  => Text(s)
      case Json.Arr(xs) => Arr(Chunk.fromIterable(xs.map(fromJson)))
      case other        => Struct(other)

  extension (e: Entry)
    def asJson: Json =
      e match
        case Text(value)   => Json.Str(value)
        case Struct(value) => value
        case Arr(values)   => Json.Arr(values.map(_.asJson))
        case Empty         => Json.Null
end Entry
