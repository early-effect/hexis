package hexis.docs

import hexis.*
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Errors extends DocSpecSuite:
  def doc = page("Errors")(
    md"""
Failures are `JevError`. It is an ADT. It does not extend `Exception`.

401 and 403 carry the TypeSafe request id. 422 is a list of Pydantic
violations. 429 and 529 retry. Other 4xx do not.

```scala
err match
  case JevError.Unauthorized(id, _) => id
  case JevError.EmptyQuestions      => None
  case _                            => err.requestId
```
""",
    exampleValue {
      JevError.EmptyQuestions.message
    }.assert(msg => assertTrue(msg == "questions must not be empty")),
  )
end Errors
