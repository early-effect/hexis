package hexis.example

import hexis.*
import zio.json.ast.Json
import zio.{Task, ZIO}

trait Extractor:
  def extract(prompt: String, schema: Json, content: String): Task[Json]

final case class FixedExtractor(record: Json) extends Extractor:
  def extract(prompt: String, schema: Json, content: String): Task[Json] =
    ZIO.succeed(record)

object Sde:
  val Fire: Probability = Probability.unsafely(0.7)

  final case class Flag(id: String, p: Probability)

  def verify(source: String, schema: Json, extraction: Json): ZIO[SystemOne, JevError, Map[String, Probability]] =
    val fields    = extraction.asObject.map(_.fields.toList).getOrElse(Nil)
    val questions = fields.flatMap { (name, value) =>
      val empty = value match
        case Json.Null | Json.Str("")   => true
        case Json.Arr(xs) if xs.isEmpty => true
        case _                          => false
      val spec = Json.Obj("path" -> Json.Str(name), "schema" -> schema)
      if empty then
        List(
          s"$name::absence_wrong" -> Noul(
            Entry.Struct(
              Json.Obj(
                "field_spec"      -> spec,
                "extracted_field" -> value,
                "main_question"   -> Json.Str("Was a supported value wrongly omitted?"),
              )
            ),
            "a value was wrongly omitted",
            "returning nothing is correct",
          )
        )
      else
        List(
          s"$name::hallucinated" -> Noul(
            Entry.Struct(
              Json.Obj(
                "field_spec"      -> spec,
                "extracted_field" -> value,
                "main_question"   -> Json.Str("Is the extracted field unsupported by the source?"),
              )
            ),
            "unsupported or absent from the source",
            "supported by the source",
          )
        )
      end if
    }.toMap
    val state = Json.Obj("source_text" -> Json.Str(source), "schema" -> schema, "extraction" -> extraction)
    SystemOne.evaluate(State.Json(state), questions).map {
      _.collect { case (k, Answer.Noul(p)) => k -> p }
    }
  end verify

  def escalate(flags: Map[String, Probability], threshold: Probability = Fire): Boolean =
    flags.exists((_, p) => p.toDouble > threshold.toDouble)

  def cascade(
      extractorCheap: Extractor,
      extractorStrong: Extractor,
      prompt: String,
      schema: Json,
      content: String,
  ): ZIO[SystemOne, JevError, Json] =
    for
      cheap <- extractorCheap.extract(prompt, schema, content).orDie
      flags <- verify(content, schema, cheap)
      out   <-
        if escalate(flags) then extractorStrong.extract(prompt, schema, content).orDie
        else ZIO.succeed(cheap)
    yield out
end Sde
