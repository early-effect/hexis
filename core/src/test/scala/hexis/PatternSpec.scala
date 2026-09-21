package hexis

import zio.test.*

object PatternSpec extends ZIOSpecDefault:
  def spec = suite("patterns")(
    test("confidence gate escalates a weak choice") {
      val ans: Answer.Choice[String] =
        Answer.Choice(
          "a",
          Map("a" -> Probability.unsafely(0.4), "b" -> Probability.unsafely(0.6)),
          Confidence.unsafely(0.2),
        )
      val g = ConfidenceGate.gate(ans, act = Confidence.unsafely(0.9), confirm = Confidence.unsafely(0.5))
      assertTrue(g == Gate.Escalate)
    },
    test("noul band keeps the middle as escalate") {
      val g = NoulBand.band(Probability.unsafely(0.5), no = Probability.unsafely(0.3), yes = Probability.unsafely(0.7))
      assertTrue(g == Gate.Escalate)
    },
    test("composite weighted mean") {
      assertTrue(math.abs(Composite.weighted(0.5 -> 2.0, 1.0 -> 1.0) - (2.0 / 3.0)) < 1e-9)
    },
  )
end PatternSpec
