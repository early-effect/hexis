package hexis

import hexis.transport.{HttpResponse, TestTransport, Transport}
import zio.{durationInt, ZLayer}
import zio.test.*

object SystemOneSpec extends ZIOSpecDefault:
  enum Team derives ChoiceDomain:
    case Billing, Technical, Sales

  private val okBody =
    """{"model":"jev-1.13.0","answers":{"department":{"type":"choice","choice":"Technical","confidence":0.74,"probabilities":{"Technical":0.83,"Sales":0.0,"Billing":0.17}},"urgency":{"type":"noul","noul":1.0}},"usage":{"input_tokens":10,"output_tokens":2}}"""

  private val cfg = Config(apiKey = ApiKey.unsafely("test-key"))

  def spec = suite("SystemOne")(
    test("evaluates a named bag against TestTransport") {
      val questions = (
        department = Choice[Team](
          "Which team",
          Team.Billing   -> "pay",
          Team.Technical -> "bug",
          Team.Sales     -> "price",
        ),
        urgency = Noul("urgent?"),
      )
      val script = TestTransport.Script(HttpResponse(200, Map.empty, okBody))
      (for out <- SystemOne.evaluate("hello", questions)
      yield assertTrue(
        out.department.choice == Team.Technical,
        out.urgency.noul.toDouble == 1.0,
      )).provide(Transport.test(script), ZLayer.succeed(cfg), SystemOne.layer)
    },
    test("fails empty questions before HTTP") {
      val script = TestTransport.Script.empty
      SystemOne
        .evaluate("hello", Map.empty[String, Question[?]])
        .flip
        .map(err => assertTrue(err == JevError.EmptyQuestions))
        .provide(Transport.test(script), ZLayer.succeed(cfg), SystemOne.layer)
    },
    test("maps 401") {
      val body   = """{"detail":{"error_type":"authentication_error","message":"bad key"}}"""
      val script = TestTransport.Script(HttpResponse(401, Map("x-typesafe-request-id" -> "req_1"), body))
      SystemOne
        .evaluate("x", Noul("yes?"))
        .flip
        .map {
          case JevError.Unauthorized(id, msg) => assertTrue(id.exists(_.value == "req_1"), msg == "bad key")
          case other                          => assertTrue(other.isInstanceOf[JevError.Unauthorized])
        }
        .provide(Transport.test(script), ZLayer.succeed(cfg), SystemOne.layer)
    },
    test("retries 429 then succeeds") {
      val limited = HttpResponse(429, Map("retry-after" -> "1"), """{"detail":{"message":"slow"}}""")
      val ok      = HttpResponse(
        200,
        Map.empty,
        """{"model":"jev-1.13.0","answers":{"value":{"type":"noul","noul":0.2}},"usage":{}}""",
      )
      val script = TestTransport.Script(limited, ok)
      (for
        fiber <- SystemOne.evaluate("x", Noul("yes?")).fork
        _     <- TestClock.adjust(2.seconds)
        out   <- fiber.join
      yield assertTrue(out match
        case Answer.Noul(p) => p.toDouble == 0.2
        case _              => false)).provide(Transport.test(script), ZLayer.succeed(cfg), SystemOne.layer)
    },
  )
end SystemOneSpec
