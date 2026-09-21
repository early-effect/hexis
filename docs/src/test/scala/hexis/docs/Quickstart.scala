package hexis.docs

import hexis.*
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Quickstart extends DocSpecSuite:
  enum Team derives ChoiceDomain:
    case Billing, Technical, Sales

  def doc = page("Quick start")(
    md"""
Build questions as values. Enums are Choice and Score domains.

```scala
val questions = (
  department = Choice[Team](
    "Which team should handle this?",
    Team.Billing   -> "Payment or subscription issues",
    Team.Technical -> "Bugs or integration problems",
    Team.Sales     -> "Pricing or account questions",
  ),
  urgency = Noul("The message conveys urgency"),
)

SystemOne.evaluate(ticket, questions)
```

`TestTransport` scripts HTTP for tests. `Transport.live` is heddle.
""",
    exampleValue {
      val q = Noul("Is this urgent?")
      Ask[Question[Boolean]].encode(q).keySet
    }.assert(keys => assertTrue(keys == Set("value"))),
  )
end Quickstart
