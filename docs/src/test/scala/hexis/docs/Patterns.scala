package hexis.docs

import hexis.*
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Patterns extends DocSpecSuite:
  def doc = page("Patterns")(
    md"""
Pattern combinators are functions on answers. They do not call Jev.

- Confidence gates: act, confirm, or escalate.
- Noul bands: yes, no, or the uncertain middle.
- Composite scores: normalize, then weight in your code.

Cookbooks (hierarchical beam search, SDE cascade) live in the unpublished
`example` module.
""",
    exampleValue {
      NoulBand.band(Probability.unsafely(0.95), no = Probability.unsafely(0.2), yes = Probability.unsafely(0.8))
    }.assert(g => assertTrue(g == Gate.Act(true))),
  )
end Patterns
