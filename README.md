# Hexis

ZIO / Scala 3 SDK for [TypeSafe System One](https://docs.typesafe.ai/introduction) (Jev).

Send a state and typed questions. Get structured answers, probabilities, and confidence.
Compose those answers in your code.

Cross-built for JVM, Scala.js, and Scala Native. HTTP is [heddle](https://github.com/early-effect/heddle) 0.4.0+.

Install, including the current version: [earlyeffect.rocks/hexis](https://www.earlyeffect.rocks/hexis/install.html).

## Quick start

```scala
import hexis.*
import zio.*

enum Team derives ChoiceDomain:
  case Billing, Technical, Sales

val questions = (
  department = Choice[Team](
    "Which team should handle this?",
    Team.Billing   -> "Payment or subscription issues",
    Team.Technical -> "Bugs or integration problems",
    Team.Sales     -> "Pricing or account questions",
  ),
  urgency = Noul("The message conveys urgency or time-sensitivity"),
)

val run =
  for
    cfg <- ZIO.fromEither(Config.fromEnv())
    out <- SystemOne.evaluate("Help, payouts have been failing for 3 days.", questions)
  yield (out.department.choice, out.urgency.noul)

// provide Transport.live ++ ZLayer.succeed(cfg) ++ SystemOne.layer
```

Tests use `Transport.test(script)` and never hit the network.

## License

Copyright [Russell White](https://github.com/russwyte). Licensed under the [Apache License, Version 2.0](LICENSE).
