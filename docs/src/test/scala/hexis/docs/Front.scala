package hexis.docs

import hexis.*
import mermoid.ascent.MermoidAscent
import mermoid.{
  ContainerFit,
  Diagram,
  LayoutConfig,
  Mermaid,
  RenderConfig,
  ResponsiveConfig,
  SequenceConfig,
  SequenceModel,
  Viewport,
}
import specular.*
import specular.ziotest.DocSpecSuite
import zio.test.*

/** The site front. Specular still emits a summary index; [[BuildSite]] copies this page over it.
  *
  * Not a sidebar item.
  */
object Front extends DocSpecSuite:

  /** Caller, SystemOne, Transport, then System One. Transport is the client dependency. The remote is
    * `POST /v1/systemone`. There is no broker.
    */
  private val evaluate =
    Mermaid("""sequenceDiagram
              |    participant Caller
              |    participant SystemOne
              |    participant Transport
              |    participant API as System One
              |    Caller->>SystemOne: state,<br/>questions
              |    SystemOne->>Transport: POST<br/>/v1/systemone
              |    Transport->>API: Http<br/>Request
              |    API-->>Transport: answers
              |    Transport-->>SystemOne: Http<br/>Response
              |    SystemOne-->>Caller: structured<br/>answers
              |""".stripMargin)

  /** Phone column is about 390px. Fit scales down to 0.72, then the column scrolls. It does not crop the scene. */
  private val shown =
    RenderConfig(
      layout = LayoutConfig(padding = 8, fontSize = 15, edgeLabelFontSize = 13, lineHeight = 18),
      sequence = SequenceConfig(
        actorMinWidth = 48,
        actorPadH = 6,
        actorHeight = 32,
        columnGap = 12,
        rowPitch = 34,
        headerGap = 8,
        footerGap = 10,
      ),
      responsive = ResponsiveConfig(fit = ContainerFit.ToWidth(0.72)),
    )

  private val phone = Some(Viewport(390))

  def doc = page("State and questions go in. Structured answers come out.")(
    illustration(MermoidAscent.diagram(evaluate, shown, phone)).assert { ui =>
      val text = ui.toString
      assertTrue(
        text.contains("mermoid-root"),
        text.contains("mermoid-fit"),
        text.contains("Caller"),
        text.contains("SystemOne"),
        text.contains("Transport"),
        text.contains("System One"),
        text.contains("state,"),
        text.contains("questions"),
        text.contains("/v1/systemone"),
        text.contains("Http"),
        text.contains("Request"),
        text.contains("Response"),
        text.contains("structured"),
        text.contains("answers"),
      )
    },
    section("Install")(
      md"""
```scala
${Install.coordinate}
```
"""
    ),
    section("SystemOne.evaluate")(
      md"""
`SystemOne.evaluate` is the client. The state and the questions go in. A structured answer comes out.
""",
      cite(
        SystemOne.evaluate(_: String, _: Question[Boolean], _: Option[Model])(using
          _: ToState[String],
          _: Ask.Aux[Question[Boolean], Answer[Boolean]],
        )
      ),
    ),
  )

  override def spec =
    suite("Front")(
      super.spec,
      test("install coordinate is 0.1.0") {
        val version = DocsVersion.version
        assertTrue(
          version == DocsVersion.published,
          Install.coordinate.contains(version),
          Install.coordinate.contains("%% \"hexis\""),
          Install.coordinate.contains("%%% \"hexis\""),
          !Install.coordinate.contains("0.1.1"),
          !Install.coordinate.contains("-ci"),
          !Install.coordinate.contains("SNAPSHOT"),
          !Install.coordinate.contains("<version>"),
          DocsVersion.advertise("0.1.1-ci") == DocsVersion.published,
          DocsVersion.advertise("0.1.1-SNAPSHOT") == DocsVersion.published,
          DocsVersion.advertise("0.1.0+4-abcdef") == DocsVersion.published,
          DocsVersion.advertise("0.1.0") == "0.1.0",
        )
      },
      test("the figure is evaluate, and fit keeps it inside a phone column") {
        val order = evaluate.diagram match
          case Diagram.Sequence(stmts) => SequenceModel.participantOrder(stmts).map(_.value)
          case _                       => Nil
        val ui    = MermoidAscent.diagram(evaluate, shown, phone)
        val width = sceneWidth(ui)
        // 0.72 is the fit floor. A 390px phone has room for about 360px of figure after page padding.
        val fits = width.exists(w => w > 0 && w * 0.72 <= 360)
        assertTrue(
          order == List("Caller", "SystemOne", "Transport", "API"),
          evaluate.source.contains("sequenceDiagram"),
          !evaluate.source.contains("broker"),
          fits,
        ).label(s"scene width $width")
      },
    )

  private def sceneWidth(ui: ascent.ast.UI[Any]): Option[Double] =
    ui match
      case element: ascent.ast.UI.Element[?] =>
        val here = element.attrs.collectFirst {
          case ascent.ast.Attr.StaticAttr("data-mermoid-width", ascent.domtypes.AttrValue.Str(raw)) =>
            raw.toDoubleOption
        }.flatten
        here.orElse(element.children.iterator.map(sceneWidth).collectFirst { case Some(w) => w })
      case ascent.ast.UI.Fragment(children) =>
        children.iterator.map(sceneWidth).collectFirst { case Some(w) => w }
      case _ => None
end Front
