package hexis.internal

import scala.scalajs.js

private[hexis] object Platform:
  val isBrowser: Boolean =
    js.typeOf(js.Dynamic.global.window) != "undefined"
