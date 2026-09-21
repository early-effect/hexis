package hexis

import hexis.wire.{WireAnswer, WireEvaluateResponse, WireQuestion}
import zio.json.*
import zio.json.ast.Json
import zio.test.*

object CodecSpec extends ZIOSpecDefault:
  def spec = suite("wire codecs")(
    test("encodes mixed questions") {
      val q = WireQuestion.from(
        Noul("The message conveys urgency")
      )
      val json = q.toJson
      assertTrue(json.contains("\"type\":\"noul\""), json.contains("urgency") || json.contains("instructions"))
    },
    test("decodes mixed live-shaped response") {
      val raw =
        """{"model":"jev-1.13.0","answers":{"department":{"type":"choice","choice":"technical","confidence":0.74,"probabilities":{"technical":0.83,"sales":0.0,"billing":0.17}},"frustration":{"type":"score","score":1.0,"confidence":1.0,"legend":{"0":"Calm, just stating facts","1":"Frustrated but civil","2":"Very angry, strong language"},"probabilities":{"0":0.0,"1":1.0,"2":0.0}},"is_urgent":{"type":"noul","noul":1.0}},"usage":{"input_tokens":425,"output_tokens":73}}"""
      val parsed = raw.fromJson[WireEvaluateResponse]
      assertTrue(
        parsed.isRight,
        parsed.exists(_.model == "jev-1.13.0"),
        parsed.exists(_.answers("is_urgent") match
          case WireAnswer.Noul(n) => n == 1.0
          case _                  => false),
        parsed.exists(_.answers("department") match
          case WireAnswer.Choice(c, _, conf) => c == "technical" && conf == 0.74
          case _                             => false),
      )
    },
    test("entry encodes structured instructions") {
      val e = Entry.Struct(Json.Obj("question" -> Json.Str("Is this urgent?")))
      assertTrue(e.asJson.asObject.isDefined)
    },
  )
end CodecSpec
