package hexis.docs

/** Install coordinate. Always the last published tag. A bare next version is not on Central. */
object DocsVersion:

  /** Last published tag. Update this in the release commit. */
  val published = "0.1.0"

  /** Returns [[published]]. A bare next version, `-ci`, `+`, and `SNAPSHOT` are unpublished. */
  def advertise(raw: String): String =
    if raw.trim == published then published else published

  /** `specular.meta.version` is the raw build version. Absent in unit tests, so those use [[published]]. */
  def version: String =
    sys.props.get("specular.meta.version").map(advertise).getOrElse(published)
end DocsVersion
