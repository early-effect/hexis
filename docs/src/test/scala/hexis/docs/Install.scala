package hexis.docs

import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

object Install extends DocSpecSuite:

  /** Site builds pass `-Dspecular.meta.version`. Doc tests load this page without it. */
  def docsVersion: String = DocsVersion.version

  def coordinate: String =
    s"""libraryDependencies += "rocks.earlyeffect" %% "hexis" % "$docsVersion"
       |// Scala.js / Native
       |libraryDependencies += "rocks.earlyeffect" %%% "hexis" % "$docsVersion"""".stripMargin

  def doc = page("Install")(
    md"""
JVM, Scala.js, and Native publish the same version.

```scala
$coordinate
```

Set `JEV_API_KEY` (or `TYPESAFE_API_KEY`) for `Transport.live`.
"""
  )

  override def spec = suite("Install")(
    test("jvm and cross coordinates share one version") {
      val version = docsVersion
      val page    = doc.children.collect { case Prose(markdown) => markdown }.mkString
      assertTrue(
        coordinate.contains(s"""%% "hexis" % "$version""""),
        coordinate.contains(s"""%%% "hexis" % "$version""""),
        page.contains(coordinate),
      )
    }
  )
end Install
