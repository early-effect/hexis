package hexis.ask

import hexis.domain.*
import hexis.error.JevError
import hexis.wire.{Decode, WireAnswer, WireQuestion}
import scala.NamedTuple.NamedTuple
import scala.compiletime.{constValue, erasedValue}

trait Ask[Q]:
  type Out
  def encode(q: Q): Map[String, WireQuestion]
  def decode(q: Q, raw: Map[String, WireAnswer]): Either[JevError, Out]

object Ask:
  type Aux[Q, O] = Ask[Q] { type Out = O }

  def apply[Q](using a: Ask[Q]): Aux[Q, a.Out] = a

  given questionAsk[A]: Ask.Aux[Question[A], Answer[A]] =
    new Ask[Question[A]]:
      type Out = Answer[A]
      def encode(q: Question[A]): Map[String, WireQuestion] =
        Map("value" -> WireQuestion.from(q))
      def decode(q: Question[A], raw: Map[String, WireAnswer]): Either[JevError, Out] =
        raw.get("value").toRight(JevError.Decode("value", "missing answer")).flatMap(decodeOne(q, _, "value"))

  given noulAsk[A]: Ask.Aux[Question.Noul[A], Answer.Noul[A]] =
    new Ask[Question.Noul[A]]:
      type Out = Answer.Noul[A]
      def encode(q: Question.Noul[A]): Map[String, WireQuestion] =
        Map("value" -> WireQuestion.from(q))
      def decode(q: Question.Noul[A], raw: Map[String, WireAnswer]): Either[JevError, Out] =
        raw
          .get("value")
          .toRight(JevError.Decode("value", "missing answer"))
          .flatMap(decodeOne(q, _, "value"))
          .map(_.asInstanceOf[Answer.Noul[A]])

  given choiceAsk[A]: Ask.Aux[Question.Choice[A], Answer.Choice[A]] =
    new Ask[Question.Choice[A]]:
      type Out = Answer.Choice[A]
      def encode(q: Question.Choice[A]): Map[String, WireQuestion] =
        Map("value" -> WireQuestion.from(q))
      def decode(q: Question.Choice[A], raw: Map[String, WireAnswer]): Either[JevError, Out] =
        raw
          .get("value")
          .toRight(JevError.Decode("value", "missing answer"))
          .flatMap(decodeOne(q, _, "value"))
          .map(_.asInstanceOf[Answer.Choice[A]])

  given scoreAsk[A]: Ask.Aux[Question.Score[A], Answer.Score[A]] =
    new Ask[Question.Score[A]]:
      type Out = Answer.Score[A]
      def encode(q: Question.Score[A]): Map[String, WireQuestion] =
        Map("value" -> WireQuestion.from(q))
      def decode(q: Question.Score[A], raw: Map[String, WireAnswer]): Either[JevError, Out] =
        raw
          .get("value")
          .toRight(JevError.Decode("value", "missing answer"))
          .flatMap(decodeOne(q, _, "value"))
          .map(_.asInstanceOf[Answer.Score[A]])

  given mapAsk[Q <: Question[?]]: Ask.Aux[Map[String, Q], Map[String, Answer[?]]] =
    new Ask[Map[String, Q]]:
      type Out = Map[String, Answer[?]]
      def encode(q: Map[String, Q]): Map[String, WireQuestion] =
        q.view.mapValues(WireQuestion.from).toMap
      def decode(
          q: Map[String, Q],
          raw: Map[String, WireAnswer],
      ): Either[JevError, Out] =
        q.foldLeft[Either[JevError, Map[String, Answer[?]]]](Right(Map.empty)):
          case (acc, (k, question)) =>
            acc.flatMap { m =>
              raw
                .get(k)
                .toRight(JevError.Decode(k, "missing answer"))
                .flatMap(decodeOne(question, _, k))
                .map(a => m.updated(k, a))
            }

  type AnswersOf[V <: Tuple] <: Tuple =
    V match
      case Question.Noul[a] *: t   => Answer.Noul[a] *: AnswersOf[t]
      case Question.Choice[a] *: t => Answer.Choice[a] *: AnswersOf[t]
      case Question.Score[a] *: t  => Answer.Score[a] *: AnswersOf[t]
      case Question[a] *: t        => Answer[a] *: AnswersOf[t]
      case EmptyTuple              => EmptyTuple

  transparent inline given named[N <: Tuple, V <: Tuple]: Ask.Aux[NamedTuple[N, V], NamedTuple[N, AnswersOf[V]]] =
    new Ask[NamedTuple[N, V]]:
      type Out = NamedTuple[N, AnswersOf[V]]
      def encode(q: NamedTuple[N, V]): Map[String, WireQuestion] =
        namesOf[N].zip(questionsOf(q)).map((n, v) => n -> WireQuestion.from(v)).toMap
      def decode(q: NamedTuple[N, V], raw: Map[String, WireAnswer]): Either[JevError, Out] =
        val names = namesOf[N]
        val qs    = questionsOf(q)
        names
          .zip(qs)
          .foldLeft[Either[JevError, List[Answer[?]]]](Right(Nil)):
            case (acc, (name, question)) =>
              acc.flatMap { as =>
                raw
                  .get(name)
                  .toRight(JevError.Decode(name, "missing answer"))
                  .flatMap(decodeOne(question, _, name))
                  .map(as :+ _)
              }
          .map { answers =>
            val tuple = answers.foldRight[Tuple](EmptyTuple)(_ *: _)
            NamedTuple[N, AnswersOf[V]](tuple.asInstanceOf[AnswersOf[V]])
          }
      end decode

  private inline def namesOf[N <: Tuple]: List[String] =
    inline erasedValue[N] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => constValue[h].asInstanceOf[String] :: namesOf[t]

  private def questionsOf[N <: Tuple, V <: Tuple](q: NamedTuple[N, V]): List[Question[?]] =
    q.toTuple.productIterator.toList.asInstanceOf[List[Question[?]]]

  private def decodeOne[A](
      q: Question[A],
      raw: WireAnswer,
      path: String,
  ): Either[JevError, Answer[A]] =
    q match
      case _: Question.Noul[A]   => Decode.noul(raw, path).asInstanceOf[Either[JevError.Decode, Answer[A]]]
      case c: Question.Choice[A] => Decode.choice(raw, c.criteria, path)
      case s: Question.Score[A]  => Decode.score(raw, s.levels, path)
end Ask
