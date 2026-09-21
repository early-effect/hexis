package hexis.example

import hexis.*
import hexis.transport.{HttpResponse, TestTransport, Transport}
import zio.json.ast.Json
import zio.{ZLayer, ZIO}
import zio.test.*

object SdeSpec extends ZIOSpecDefault:
  def spec = suite("SDE cascade")(
    test("escalates a hallucinated field and keeps the strong record") {
      val cheap = Json.Obj(
        "registration_open_date" -> Json.Str(""),
        "description"            -> Json.Str("Registration opens for the fall semester"),
      )
      val strong = Json.Obj(
        "registration_open_date" -> Json.Str(""),
        "description"            -> Json.Str(""),
      )
      val body =
        """{"model":"jev-1.13.0","answers":{"registration_open_date::absence_wrong":{"type":"noul","noul":0.14},"description::hallucinated":{"type":"noul","noul":0.95}},"usage":{}}"""
      val script = TestTransport.Script(HttpResponse(200, Map.empty, body))
      val cfg    = Config(apiKey = ApiKey.unsafely("test"))
      Sde
        .cascade(FixedExtractor(cheap), FixedExtractor(strong), "extract", Json.Obj(), "page with no date")
        .map(out => assertTrue(out == strong))
        .provide(Transport.test(script), ZLayer.succeed(cfg), SystemOne.layer)
    }
  )
end SdeSpec
