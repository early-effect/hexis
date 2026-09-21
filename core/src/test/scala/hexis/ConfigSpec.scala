package hexis

import zio.test.*

object ConfigSpec extends ZIOSpecDefault:
  def spec = suite("Config")(
    test("prefers JEV_API_KEY over TYPESAFE_API_KEY") {
      val env = Map("JEV_API_KEY" -> "jev-key", "TYPESAFE_API_KEY" -> "ts-key")
      val cfg = Config.fromEnv(env.get)
      assertTrue(cfg.exists(_.apiKey.reveal == "jev-key"))
    },
    test("falls back to TYPESAFE_API_KEY") {
      val cfg = Config.fromEnv(Map("TYPESAFE_API_KEY" -> "ts-key").get)
      assertTrue(cfg.exists(_.apiKey.reveal == "ts-key"))
    },
    test("ignores blank env values") {
      val cfg = Config.fromEnv(Map("JEV_API_KEY" -> "  ", "TYPESAFE_API_KEY" -> "ts-key").get)
      assertTrue(cfg.exists(_.apiKey.reveal == "ts-key"))
    },
    test("constructor key wins") {
      val cfg = Config.fromEnv(Map("JEV_API_KEY" -> "jev-key").get, apiKey = Some("explicit"))
      assertTrue(cfg.exists(_.apiKey.reveal == "explicit"))
    },
  )
end ConfigSpec
