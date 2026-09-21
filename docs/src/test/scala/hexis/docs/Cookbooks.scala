package hexis.docs

import hexis.*
import hexis.example.Hierarchy
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Cookbooks extends DocSpecSuite:
  def doc = page("Cookbooks")(
    md"""
Hierarchical beam search and the SDE cascade live in the unpublished
`example` module. They are not published Hexis API.

`Hierarchy.fixture` is a tiny in-repo tree. Beam width is 3. Path score is
the geometric mean of edge probabilities.

The SDE example takes an `Extractor` algebra. Tests fake it. Hexis does
not depend on a chat-model SDK.
""",
    exampleValue {
      Hierarchy.fixture.children.keySet
    }.assert(keys => assertTrue(keys == Set("drinkware", "sporting"))),
  )
end Cookbooks
