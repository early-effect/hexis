package hexis

import zio.{ZIO, ZLayer}
import zio.test.*

object LiveSpec extends ZIOSpecDefault:
  def spec =
    suite("live TypeSafe")(
      test("mixed evaluate and models list") {
        val questions = (
          department = Choice.dynamic(
            "Which team should handle this",
            Map(
              "billing"   -> "Payment or subscription issues",
              "technical" -> "Bugs or integration problems",
              "sales"     -> "Pricing or account questions",
            ),
          ),
          urgency = Noul("The message conveys urgency or time-sensitivity"),
        )
        val state =
          "Hi, I have been trying to connect my Stripe account for 3 days and the integration keeps failing."
        for
          cfg    <- ZIO.fromEither(Config.fromEnv().left.map(IllegalStateException(_)))
          out    <- SystemOne.evaluate(state, questions).provide(ZLayer.succeed(cfg), Transport.live, SystemOne.layer)
          models <- SystemOne.models.provide(ZLayer.succeed(cfg), Transport.live, SystemOne.layer)
        yield assertTrue(
          out.department.choice.nonEmpty,
          out.urgency.noul.toDouble >= 0.0,
          models.nonEmpty,
        )
      }
    ) @@ TestAspect.ifEnvSet("JEV_API_KEY") @@ TestAspect.tag("hexis.live") @@ TestAspect.withLiveClock
end LiveSpec
