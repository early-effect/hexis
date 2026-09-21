package hexis.example

import hexis.*
import hexis.transport.{HttpRequest, HttpResponse, Transport}
import zio.{ZLayer, Chunk}
import zio.test.*

object HierarchySpec extends ZIOSpecDefault:
  private def choiceBody(winner: String, probs: Map[String, Double]): String =
    val fields = probs.map((k, v) => s""""$k":$v""").mkString(",")
    s"""{"model":"jev-1.13.0","answers":{"value":{"type":"choice","choice":"$winner","confidence":1.0,"probabilities":{$fields}}},"usage":{}}"""

  def spec = suite("hierarchy")(
    test("beam recovers from a greedy trap") {
      val respond: HttpRequest => HttpResponse | hexis.transport.TransportError = req =>
        val body    = req.body.getOrElse("")
        val payload =
          if body.contains("\"mugs\"") then choiceBody("mugs", Map("mugs" -> 0.95, "tumblers" -> 0.05))
          else if body.contains("\"bats\"") then choiceBody("bats", Map("bats" -> 0.51, "balls" -> 0.49))
          else choiceBody("sporting", Map("sporting" -> 0.55, "drinkware" -> 0.45))
        HttpResponse(200, Map.empty, payload)
      val cfg = Config(apiKey = ApiKey.unsafely("test"))
      (for
        greedy <- Hierarchy.greedy(Hierarchy.fixture, "a ceramic coffee mug")
        beam   <- Hierarchy.beam(Hierarchy.fixture, "a ceramic coffee mug", width = 2)
      yield assertTrue(
        greedy == Chunk("sporting", "bats"),
        beam.head.path == Chunk("drinkware", "mugs"),
      )).provide(Transport.test(respond), ZLayer.succeed(cfg), SystemOne.layer)
    }
  )
end HierarchySpec
