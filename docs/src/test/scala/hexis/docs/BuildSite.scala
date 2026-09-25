package hexis.docs

import earlyeffect.docs.EarlyEffectTheme
import specular.site.*
import zio.*

import java.nio.file.Path

object BuildSite extends DocsSite:

  def pages =
    Vector(Overview.doc, Install.doc, Quickstart.doc, Transport.doc, Patterns.doc, Errors.doc, Cookbooks.doc)

  override def site: SiteModel =
    val m       = meta
    val branded = EarlyEffectTheme.brand(super.site)
    branded.copy(
      summaryMarkdown = Some(
        """**Hexis** is a ZIO SDK for TypeSafe System One. Typed questions, structured
answers, probabilities you can branch on. HTTP is heddle on JVM, JS, and Native.
"""
      ),
      brand = Some(
        Brand(
          name = m.title.getOrElse("hexis"),
          links = Vector(EarlyEffectTheme.github("https://github.com/early-effect/hexis")),
        )
      ),
      installSnippets = Vector(CodeSnippet("Install", Install.coordinate)),
    )
  end site

  override def layers: ZLayer[Any, Nothing, SiteBuilder] =
    EarlyEffectTheme.layers

  override def afterBuild(out: Path, result: SiteOutput): Task[Unit] =
    val _ = result
    EarlyEffectTheme.writeLogo(out)
end BuildSite
