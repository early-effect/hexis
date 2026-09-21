package hexis.example

import hexis.*
import hexis.transport.{HttpRequest, HttpResponse, Transport}
import zio.ZLayer
import zio.test.*

object DemosSpec extends ZIOSpecDefault:
  private val cfg = Config(apiKey = ApiKey.unsafely("test"))

  private def ok(answers: String): HttpResponse =
    HttpResponse(200, Map.empty, s"""{"model":"jev-1.13.0","answers":{$answers},"usage":{}}""")

  def spec = suite("demos")(
    test("fan-out returns mixed primitives") {
      val body = ok(
        """"topic":{"type":"choice","choice":"billing","confidence":0.9,"probabilities":{"billing":0.9,"technical":0.1,"other":0.0}},"refund":{"type":"noul","noul":0.8},"urgency":{"type":"score","score":2.0,"confidence":1.0,"legend":{"0":"can wait","1":"this week","2":"today"},"probabilities":{"0":0.0,"1":0.0,"2":1.0}}"""
      )
      Demos
        .fanOut("refund my last invoice")
        .map { (topic, refund, urgency) =>
          assertTrue(topic == "billing", refund.toDouble == 0.8, urgency == 1.0)
        }
        .provide(Transport.test(_ => body), ZLayer.succeed(cfg), SystemOne.layer)
    },
    test("composite weights normalized scores") {
      val body = ok(
        """"spam":{"type":"noul","noul":0.2},"abuse":{"type":"noul","noul":0.0},"priority":{"type":"score","score":2.0,"confidence":1.0,"legend":{"0":"low","1":"medium","2":"high"},"probabilities":{"0":0.0,"1":0.0,"2":1.0}}"""
      )
      Demos
        .composite("please help")
        .map(score => assertTrue(math.abs(score - 0.3) < 1e-9))
        .provide(Transport.test(_ => body), ZLayer.succeed(cfg), SystemOne.layer)
    },
    test("confidence gate escalates a weak intent") {
      val body = ok(
        """"value":{"type":"choice","choice":"support","confidence":0.2,"probabilities":{"balance":0.2,"transfer":0.2,"support":0.6}}"""
      )
      Demos
        .route("hmm")
        .map(g => assertTrue(g == Gate.Escalate))
        .provide(Transport.test(_ => body), ZLayer.succeed(cfg), SystemOne.layer)
    },
    test("function-calling keeps a closed set") {
      val body = ok(
        """"needsTool":{"type":"noul","noul":0.95},"tool":{"type":"choice","choice":"create_refund","confidence":0.88,"probabilities":{"get_balance":0.05,"create_refund":0.9,"open_ticket":0.05}}"""
      )
      Demos
        .functionCall("refund order 12")
        .map(out => assertTrue(out.contains("create_refund")))
        .provide(Transport.test((_: HttpRequest) => body), ZLayer.succeed(cfg), SystemOne.layer)
    },
  )
end DemosSpec
