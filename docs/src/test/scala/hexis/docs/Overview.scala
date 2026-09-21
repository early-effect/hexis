package hexis.docs

import hexis.*
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Overview extends DocSpecSuite:
  def doc = page("Overview")(
    md"""
**Hexis** is a ZIO / Scala 3 SDK for TypeSafe System One (Jev).

Send a state and typed questions. Get structured answers, probabilities, and
confidence. Compose those answers in code. That is System 1 compiled into software.

The live HTTP client is heddle on JVM, Scala.js, and Native.
""",
    exampleValue {
      Probability.unsafely(0.95)
    }.assert(p => assertTrue(p.toDouble == 0.95)),
  )
end Overview
