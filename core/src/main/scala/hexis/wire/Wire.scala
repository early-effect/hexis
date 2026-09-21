package hexis.wire

import hexis.domain.*
import hexis.error.JevError
import zio.Chunk
import zio.json.*
import zio.json.ast.Json

enum WireQuestion:
  case Noul(instructions: Json, criteria: Option[Json])
  case Choice(instructions: Json, criteria: Json)
  case Score(instructions: Json, criteria: Json)

object WireQuestion:
  def from[A](q: Question[A]): WireQuestion =
    q match
      case Question.Noul(instructions, criteria) =>
        val crit = criteria.map(c =>
          Json.Obj(
            "true"  -> c.yes.asJson,
            "false" -> c.no.asJson,
          )
        )
        Noul(instructions.asJson, crit)
      case Question.Choice(instructions, criteria) =>
        val fields = criteria.pairs.iterator.map((a, e) => Criteria.label(a) -> e.asJson).toList
        Choice(instructions.asJson, Json.Obj(Chunk.fromIterable(fields)))
      case Question.Score(instructions, levels) =>
        Score(instructions.asJson, Json.Arr(Chunk.fromIterable(levels.pairs.iterator.map((_, e) => e.asJson).toList)))

  given JsonEncoder[WireQuestion] = JsonEncoder[Json].contramap:
    case Noul(instructions, criteria) =>
      val base = List("type" -> Json.Str("noul"), "instructions" -> instructions)
      Json.Obj(Chunk.fromIterable(criteria.fold(base)(c => base :+ ("criteria" -> c))))
    case Choice(instructions, criteria) =>
      Json.Obj("type" -> Json.Str("choice"), "instructions" -> instructions, "criteria" -> criteria)
    case Score(instructions, criteria) =>
      Json.Obj("type" -> Json.Str("score"), "instructions" -> instructions, "criteria" -> criteria)
end WireQuestion

enum WireAnswer:
  case Noul(noul: Double)
  case Choice(choice: String, probabilities: Map[String, Double], confidence: Double)
  case Score(
      score: Double,
      probabilities: Map[String, Double],
      legend: Map[String, Json],
      confidence: Double,
  )

object WireAnswer:
  given JsonDecoder[WireAnswer] = JsonDecoder[Json].mapOrFail:
    case obj: Json.Obj =>
      obj.get("type").flatMap(_.asString) match
        case Some("noul") =>
          asDouble(obj.get("noul")).map(Noul.apply).toRight("noul answer missing noul")
        case Some("choice") =>
          for
            choice <- obj.get("choice").flatMap(_.asString).toRight("choice answer missing choice")
            conf   <- asDouble(obj.get("confidence")).toRight("choice missing confidence")
            probs  <- decodeNumMap(obj.get("probabilities"), "probabilities")
          yield Choice(choice, probs, conf)
        case Some("score") =>
          for
            score  <- asDouble(obj.get("score")).toRight("score answer missing score")
            conf   <- asDouble(obj.get("confidence")).toRight("score missing confidence")
            probs  <- decodeNumMap(obj.get("probabilities"), "probabilities")
            legend <- decodeJsonMap(obj.get("legend"), "legend")
          yield Score(score, probs, legend, conf)
        case Some(other) => Left(s"unknown answer type: $other")
        case None        => Left("answer missing type")
    case _ => Left("answer must be an object")

  private def asDouble(json: Option[Json]): Option[Double] =
    json.flatMap(_.asNumber).map(_.value.doubleValue)

  private def decodeNumMap(json: Option[Json], field: String): Either[String, Map[String, Double]] =
    json match
      case Some(obj: Json.Obj) =>
        obj.fields.foldLeft[Either[String, Map[String, Double]]](Right(Map.empty)):
          case (acc, (k, v)) =>
            acc.flatMap { m =>
              asDouble(Some(v)).toRight(s"$field.$k is not a number").map(n => m.updated(k, n))
            }
      case _ => Left(s"$field must be an object")

  private def decodeJsonMap(json: Option[Json], field: String): Either[String, Map[String, Json]] =
    json match
      case Some(obj: Json.Obj) => Right(obj.fields.toMap)
      case _                   => Left(s"$field must be an object")
end WireAnswer

final case class WireEvaluateRequest(state: Json, model: String, questions: Map[String, WireQuestion])

object WireEvaluateRequest:
  given JsonEncoder[WireEvaluateRequest] = DeriveJsonEncoder.gen

final case class WireEvaluateResponse(model: String, answers: Map[String, WireAnswer], usage: WireUsage)

object WireEvaluateResponse:
  given JsonDecoder[WireEvaluateResponse] = DeriveJsonDecoder.gen

final case class WireUsage(input_tokens: Option[Int], output_tokens: Option[Int])

object WireUsage:
  given JsonDecoder[WireUsage] = DeriveJsonDecoder.gen

  def toDomain(u: WireUsage): Usage = Usage(u.input_tokens, u.output_tokens)

final case class WireModelsResponse(models: List[WireModelCard])

object WireModelsResponse:
  given JsonDecoder[WireModelsResponse] = DeriveJsonDecoder.gen

final case class WireModelCard(name: String, description: String, release_date: String)

object WireModelCard:
  given JsonDecoder[WireModelCard] = DeriveJsonDecoder.gen

  def toDomain(c: WireModelCard): ModelCard = ModelCard(c.name, c.description, c.release_date)

object Decode:
  def noul(raw: WireAnswer, path: String): Either[JevError, Answer[Boolean]] =
    raw match
      case WireAnswer.Noul(n) =>
        Probability(n).left.map(msg => JevError.Decode(path, msg)).map(Answer.Noul.apply)
      case _ => Left(JevError.Decode(path, "expected noul answer"))

  def choice[A](raw: WireAnswer, criteria: Criteria[A], path: String): Either[JevError, Answer.Choice[A]] =
    raw match
      case WireAnswer.Choice(choice, probs, conf) =>
        val byLabel = criteria.pairs.iterator.map((a, _) => Criteria.label(a) -> a).toMap
        for
          selected <- byLabel.get(choice).toRight(JevError.Decode(path, s"unknown choice $choice"))
          mapped   <- remap(probs, byLabel, path)
          c        <- Confidence(conf).left.map(msg => JevError.Decode(path, msg))
        yield Answer.Choice(selected, mapped, c)
      case _ => Left(JevError.Decode(path, "expected choice answer"))

  def score[A](raw: WireAnswer, levels: Levels[A], path: String): Either[JevError, Answer.Score[A]] =
    raw match
      case WireAnswer.Score(score, probs, legend, conf) =>
        val byIndex = levels.keys.zipWithIndex.map((a, i) => i.toString -> a).toMap
        for
          mapped <- remap(probs, byIndex, path)
          c      <- Confidence(conf).left.map(msg => JevError.Decode(path, msg))
        yield
          val entries = byIndex.flatMap { (k, a) =>
            legend.get(k).map(j => a -> Entry.fromJson(j))
          }
          Answer.Score(score, mapped, entries, c, levels.keys)
      case _ => Left(JevError.Decode(path, "expected score answer"))

  private def remap[A](
      probs: Map[String, Double],
      byLabel: Map[String, A],
      path: String,
  ): Either[JevError, Map[A, Probability]] =
    probs.foldLeft[Either[JevError, Map[A, Probability]]](Right(Map.empty)):
      case (acc, (k, v)) =>
        acc.flatMap { m =>
          byLabel.get(k) match
            case None    => Left(JevError.Decode(path, s"unknown key $k"))
            case Some(a) =>
              Probability(v).left.map(msg => JevError.Decode(path, msg)).map(p => m.updated(a, p))
        }
end Decode
