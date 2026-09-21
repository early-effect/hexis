package hexis

import zio.test.*

object AskSpec extends ZIOSpecDefault:
  enum Team derives ChoiceDomain:
    case Billing, Technical, Sales

  enum Frustration derives ScoreDomain:
    case Calm, Frustrated, Angry

  def spec = suite("Ask")(
    test("named tuple encodes question keys") {
      val questions = (
        department = Choice[Team](
          "Which team should handle this?",
          Team.Billing   -> "Payment",
          Team.Technical -> "Bugs",
          Team.Sales     -> "Pricing",
        ),
        urgency = Noul("The message conveys urgency"),
      )
      val encoded = summon[Ask[(department: Question[Team], urgency: Question[Boolean])]].encode(questions)
      assertTrue(encoded.keySet == Set("department", "urgency"))
    },
    test("dynamic map encodes loop-built keys") {
      val q = Map(
        "a" -> Noul("Is A true?"),
        "b" -> Noul("Is B true?"),
      )
      val encoded = Ask.mapAsk.encode(q)
      assertTrue(encoded.keySet == Set("a", "b"))
    },
    test("choice requires the full domain") {
      val boom = scala.util.Try(
        Choice[Team]("team", Team.Billing -> "only billing")
      )
      assertTrue(boom.isFailure)
    },
    test("score requires at least two levels") {
      val boom = scala.util.Try(Score.dynamic("rate", Vector("only")))
      assertTrue(boom.isFailure)
    },
  )
end AskSpec
