package hexis

import heddle.*
import hexis.transport.{HttpMethod, HttpRequest, Transport}
import zio.{durationInt, ZIO, ZLayer}
import zio.test.*

object HeddleAdapterSpec extends ZIOSpecDefault:
  def spec =
    suite("HeddleTransport")(
      test("posts JSON through an in-process heddle server") {
        val routes = Routes(
          Method.POST / "v1" / "systemone" -> Handler.fromFunctionZIO { (req: Request) =>
            req.body.utf8.orDie.map { body =>
              if body.contains("\"questions\"") then
                Response.json(
                  """{"model":"jev-1.13.0","answers":{"value":{"type":"noul","noul":0.91}},"usage":{"input_tokens":1,"output_tokens":1}}"""
                )
              else Response.text("bad", Status.BadRequest)
            }
          }
        )
        val cfg   = Config(apiKey = ApiKey.unsafely("test-key"), baseUrl = "http://unused")
        val local = Server.Config.default.copy(host = "127.0.0.1", port = 0)
        ZIO.scoped {
          Server.install(routes, local).flatMap { server =>
            server.port.flatMap { port =>
              val req = HttpRequest(
                HttpMethod.Post,
                s"http://127.0.0.1:$port/v1/systemone",
                Map("content-type" -> "application/json"),
                Some(
                  """{"state":"x","model":"jev-latest","questions":{"value":{"type":"noul","instructions":"yes?"}}}"""
                ),
                5.seconds,
              )
              (for
                t   <- ZIO.service[Transport]
                res <- t.execute(req).mapError(e => RuntimeException(e.toString))
              yield assertTrue(res.status == 200, res.body.contains("0.91")))
                .provide(ZLayer.succeed(cfg) >>> Transport.live)
            }
          }
        }
      }
    ) @@ TestAspect.withLiveClock
end HeddleAdapterSpec
