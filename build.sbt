import org.scalajs.linker.interface.ModuleKind
import scala.scalanative.sbtplugin.ScalaNativePlugin.autoImport.{NativeTags, nativeConfig}

lazy val nativeThreads: Seq[sbt.Setting[?]] =
  Seq(nativeConfig ~= (_.withMultithreading(true)))

lazy val nativeOpenssl: Seq[sbt.Setting[?]] =
  val brew = java.io.File("/opt/homebrew/opt/openssl@3")
  val (cflags, libs) =
    if brew.isDirectory then
      (Seq(s"-I${brew.getPath}/include"), Seq(s"-L${brew.getPath}/lib", "-lssl", "-lcrypto"))
    else (Seq.empty[String], Seq("-lssl", "-lcrypto"))
  Seq(
    nativeConfig ~= { c =>
      c.withCompileOptions(c.compileOptions ++ cflags)
        .withLinkingOptions(c.linkingOptions ++ libs)
    }
  )

MyVersions.settings
HexisZipx.settings

val scala3Version: String = MyVersions.scala
val scalaVersions         = Seq(scala3Version)

Global / concurrentRestrictions ++= Seq(
  Tags.limit(NativeTags.Link, 1),
  Tags.limit(Tags.Compile, 4),
)

organization         := "rocks.earlyeffect"
organizationName     := "Early Effect"
organizationHomepage := Some(uri("https://www.earlyeffect.rocks"))
licenses             := List("Apache-2.0" -> uri("http://www.apache.org/licenses/LICENSE-2.0.txt"))
homepage             := Some(uri("https://github.com/early-effect/hexis"))
scmInfo              := Some(
  ScmInfo(
    uri("https://github.com/early-effect/hexis"),
    "scm:git@github.com:early-effect/hexis.git",
  )
)
developers := List(
  Developer(
    id = "russwyte",
    name = "Russ White",
    email = "356303+russwyte@users.noreply.github.com",
    url = uri("https://github.com/russwyte"),
  )
)
versionScheme := Some("early-semver")

publishTo := {
  val centralSnapshots = "https://central.sonatype.com/repository/maven-snapshots/"
  if (isSnapshot.value) Some("central-snapshots" at centralSnapshots)
  else localStaging.value
}
publishMavenStyle    := true
pomIncludeRepository := { _ => false }

// CI-only publishing: the signing key hex comes from the PGP_KEY_HEX env var, set by
// the shared early-effect org secret in the generated release job. There is no real key
// in this file: the "MISSING_KEY_HEX" sentinel keeps the build loadable for local
// compile/test but makes signing fail loudly if anyone tries to publish off-CI.
usePgpKeyHex(sys.env.getOrElse("PGP_KEY_HEX", "MISSING_KEY_HEX"))

lazy val commonSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-Wunused:all",
    "-feature",
  ),
  testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework"),
  Test / testOptions += Tests.Argument("-ignore-tags", "hexis.live"),
)

lazy val root = project
  .in(file("."))
  .aggregate((hexis.projectRefs ++ example.projectRefs ++ Seq[sbt.ProjectReference](docs))*)
  .settings(
    name           := "hexis-root",
    publish / skip := true,
    test / skip    := true,
  )

lazy val hexis = (projectMatrix in file("core"))
  .settings(commonSettings)
  .settings(MyVersions.coreLib)
  .settings(MyVersions.coreTest)
  .settings(
    name        := "hexis",
    description := "ZIO SDK for TypeSafe System One (Jev)",
    publishMavenStyle    := true,
    pomIncludeRepository := { _ => false },
  )
  .jvmPlatform(scalaVersions = scalaVersions)
  .jsPlatform(
    scalaVersions = scalaVersions,
    MyVersions.jsRuntime ++ Seq(
      scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.CommonJSModule))
    ),
  )
  .nativePlatform(scalaVersions = scalaVersions, MyVersions.nativeJavaTime ++ nativeThreads ++ nativeOpenssl)

lazy val example = (projectMatrix in file("example"))
  .dependsOn(hexis)
  .settings(commonSettings)
  .settings(MyVersions.coreLib)
  .settings(MyVersions.coreTest)
  .settings(
    name           := "hexis-example",
    publish / skip := true,
  )
  .jvmPlatform(scalaVersions = scalaVersions)

lazy val docs = project
  .in(file("docs"))
  .dependsOn(hexis.jvm(scala3Version), example.jvm(scala3Version))
  .enablePlugins(SpecularPlugin)
  .settings(commonSettings)
  .settings(
    name            := "hexis-docs",
    publish / skip  := true,
    publishArtifact := false,
    zipxPublish     := Some(false),
    libraryDependencySchemes += "rocks.earlyeffect" %% "heddle" % VersionScheme.Always,
    scalacOptions ++= Seq("-language:implicitConversions"),
    MyVersions.docsTest,
    MyVersions.coreTest,
    Test / mainClass       := Some("specular.site.DocsServe"),
    Test / run / mainClass := (Test / mainClass).value,
    specularBuildMain      := "hexis.docs.BuildSite",
    specularMetaProject    := Some(LocalProject("hexis")),
    specularArtifactKind   := "library",
    specularSiteDirectory  := (ThisBuild / baseDirectory).value / "target" / "site",
    specularDisplayVersion := stripCi,
  )

addCommandAlias("docsPreview", "~docs/specularPreview")
addCommandAlias("testJVM", "hexis/testFull; example/testFull; docs/testFull")
addCommandAlias("testJS", "hexisJS/testFull")
addCommandAlias("testNative", "hexisNative/testFull")
addCommandAlias("testLive", "set hexis / Test / testOptions := Seq(Tests.Argument(\"-tags\", \"hexis.live\")); hexis/testOnly hexis.LiveSpec")
