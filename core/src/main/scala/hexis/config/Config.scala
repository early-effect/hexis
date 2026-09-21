package hexis.config

import hexis.domain.{ApiKey, Model}
import zio.{Config as ZConfig, Duration}

final case class Retry(
    maxRetries: Int = 2,
    backoffInitial: Duration = Duration.fromMillis(500),
    backoffMax: Duration = Duration.fromSeconds(5),
    backoffJitter: Double = 0.25,
    httpStatuses: Set[Int] = Set(408, 429) ++ (500 to 599),
    respectRetryAfter: Boolean = true,
    retryUnreachable: Boolean = true,
    retryTimeout: Boolean = true,
    budget: Option[Duration] = Some(Duration.fromSeconds(30)),
)

object Retry:
  val default: Retry = Retry()

final case class HeddleSettings(
    maxConnectionsPerHost: Int = 10,
    maxIdlePerHost: Int = 10,
    connectTimeout: Duration = Duration.fromSeconds(10),
    idleTimeout: Duration = Duration.fromSeconds(60),
    poolIdleTimeout: Duration = Duration.fromSeconds(60),
    maxHeaderBytes: Long = 64L * 1024,
    maxBodyBytes: Long = 10L * 1024 * 1024,
)

object HeddleSettings:
  val default: HeddleSettings = HeddleSettings()

final case class Config(
    apiKey: ApiKey,
    baseUrl: String = Config.DefaultBaseUrl,
    model: Model = Model.Latest,
    timeout: Duration = Duration.fromSeconds(10),
    retry: Retry = Retry.default,
    headers: Map[String, String] = Map.empty,
    allowBrowser: Boolean = false,
    heddle: HeddleSettings = HeddleSettings.default,
    userAgent: String = Config.DefaultUserAgent,
)

object Config:
  val DefaultBaseUrl: String   = "https://api.typesafe.ai"
  val DefaultUserAgent: String = "hexis/0.0.0"
  val ApiKeyEnv: String        = "JEV_API_KEY"
  val ApiKeyEnvAlt: String     = "TYPESAFE_API_KEY"
  val BaseUrlEnv: String       = "TYPESAFE_BASE_URL"
  val DefaultModelEnv: String  = "TYPESAFE_DEFAULT_MODEL"

  def fromEnv(
      env: String => Option[String] = sys.env.get,
      apiKey: Option[String] = None,
  ): Either[String, Config] =
    val keyRaw =
      firstNonEmpty(apiKey) orElse firstNonEmpty(env(ApiKeyEnv)) orElse firstNonEmpty(env(ApiKeyEnvAlt))
    for
      raw <- keyRaw.toRight("missing API key (JEV_API_KEY or TYPESAFE_API_KEY)")
      key <- ApiKey(raw)
      base  = firstNonEmpty(env(BaseUrlEnv)).getOrElse(DefaultBaseUrl)
      model = firstNonEmpty(env(DefaultModelEnv)).map(Model.parse).getOrElse(Model.Latest)
    yield Config(apiKey = key, baseUrl = base.stripSuffix("/"), model = model)
  end fromEnv

  val descriptor: ZConfig[Config] =
    (ZConfig.string("apiKey") ++
      ZConfig.string("baseUrl").withDefault(DefaultBaseUrl) ++
      ZConfig.string("model").withDefault("jev-latest") ++
      ZConfig.duration("timeout").withDefault(Duration.fromSeconds(10)))
      .nested("hexis")
      .mapOrFail { (apiKey, baseUrl, model, timeout) =>
        ApiKey(apiKey)
          .map { k =>
            Config(apiKey = k, baseUrl = baseUrl.stripSuffix("/"), model = Model.parse(model), timeout = timeout)
          }
          .left
          .map(msg => zio.Config.Error.InvalidData(zio.Chunk("hexis", "apiKey"), msg))
      }

  private def firstNonEmpty(value: Option[String]): Option[String] =
    value.map(_.trim).filter(_.nonEmpty)
end Config
