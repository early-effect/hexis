package hexis.docs

import hexis.*
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Transport extends DocSpecSuite:
  def doc = page("Transport")(
    md"""
`SystemOne` talks HTTP through `Transport`. Live traffic is heddle on JVM,
Scala.js (Node fetch), and Native. Tests use `Transport.test`.

Retry, status mapping, and JSON live in `SystemOne`, not in `Transport`.
That is what keeps `TestTransport` honest.

```scala
val script = TestTransport.Script(HttpResponse(200, Map.empty, body))
SystemOne.evaluate(state, questions)
  .provide(Transport.test(script), ZLayer.succeed(cfg), SystemOne.layer)
```

`Transport.heddle(client)` injects an existing heddle `Client`.
""",
    exampleValue {
      JevError.TimedOut.message
    }.assert(msg => assertTrue(msg == "request timed out")),
  )
end Transport
