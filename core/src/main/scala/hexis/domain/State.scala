package hexis.domain

import zio.json.JsonEncoder
import zio.json.ast.Json

enum State:
  case Text(value: String)
  case Json(value: zio.json.ast.Json)

object State:
  def text(value: String): State = Text(value)

  def json(value: zio.json.ast.Json): State = Json(value)

  def of[A: ToState](value: A): Either[String, State] = summon[ToState[A]].toState(value)

  extension (s: State)
    def asJson: zio.json.ast.Json =
      s match
        case Text(value) => zio.json.ast.Json.Str(value)
        case Json(value) => value
end State

trait ToState[-A]:
  def toState(a: A): Either[String, State]

object ToState:
  def apply[A](using t: ToState[A]): ToState[A] = t

  given ToState[String] with
    def toState(a: String): Either[String, State] = Right(State.Text(a))

  given ToState[State] with
    def toState(a: State): Either[String, State] = Right(a)

  given ToState[Json] with
    def toState(a: Json): Either[String, State] = Right(State.Json(a))

  def encoded[A](using enc: JsonEncoder[A]): ToState[A] =
    (a: A) => enc.toJsonAST(a).map(State.Json.apply)
end ToState
