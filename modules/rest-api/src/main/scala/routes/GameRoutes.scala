package org.maichess.mono.api.routes

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import org.http4s.{EntityDecoder, HttpRoutes, Response}
import org.http4s.dsl.io.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityEncoder.circeEntityEncoder
import org.maichess.mono.api.bridge.{GameHub, ModelBridge}
import org.maichess.mono.api.codec.Codecs.given
import org.maichess.mono.api.model.*
import org.maichess.mono.uifx.ClockConfig

object GameRoutes:
  def apply(hub: GameHub): HttpRoutes[IO] =
    given EntityDecoder[IO, NewGameRequest] = jsonOf[IO, NewGameRequest]
    given EntityDecoder[IO, MoveRequest]    = jsonOf[IO, MoveRequest]
    given EntityDecoder[IO, FenRequest]     = jsonOf[IO, FenRequest]
    given EntityDecoder[IO, PgnRequest]     = jsonOf[IO, PgnRequest]

    HttpRoutes.of[IO]:
      case GET -> Root / "game" =>
        currentState(hub).flatMap(Ok(_))

      case req @ POST -> Root / "game" =>
        req.as[NewGameRequest].flatMap { body =>
          ModelBridge.lookupBot(body.whiteBot) match
            case Left(e)  => BadRequest(err(e))
            case Right(wb) =>
              ModelBridge.lookupBot(body.blackBot) match
                case Left(e)  => BadRequest(err(e))
                case Right(bb) =>
                  IO.delay(hub.newGame(
                    white       = wb,
                    black       = bb,
                    whiteName   = body.whiteName.getOrElse("White"),
                    blackName   = body.blackName.getOrElse("Black"),
                    clockConfig = body.clockConfig.map(c => ClockConfig(c.initialMs, c.incrementMs))
                  )) *> currentState(hub).flatMap(Created(_))
        }

      case req @ POST -> Root / "game" / "move" =>
        req.as[MoveRequest].flatMap { body =>
          IO.delay(hub.model.state.game.current).flatMap { sit =>
            ModelBridge.decodeMove(body, sit) match
              case Left(e)     => BadRequest(err(e))
              case Right(move) =>
                IO.delay(hub.model.applyMove(move)).flatMap {
                  case Right(_)    => currentState(hub).flatMap(Ok(_))
                  case Left(illgl) => UnprocessableEntity(err(illgl.reason))
                }
          }
        }

      case POST -> Root / "game" / "undo" =>
        IO.delay {
          val before = hub.model.state.game.history.length
          hub.model.undo()
          hub.model.state.game.history.length < before
        }.flatMap {
          case true  => currentState(hub).flatMap(Ok(_))
          case false => Conflict(err("Cannot undo: no history, or pause the game first when a clock is active"))
        }

      case POST -> Root / "game" / "redo" =>
        IO.delay {
          val before = hub.model.state.game.future.length
          hub.model.redo()
          hub.model.state.game.future.length < before
        }.flatMap {
          case true  => currentState(hub).flatMap(Ok(_))
          case false => Conflict(err("Cannot redo: no future, or pause the game first when a clock is active"))
        }

      case POST -> Root / "game" / "resign" =>
        IO.delay(hub.resign()) *> currentState(hub).flatMap(Ok(_))

      case POST -> Root / "game" / "pause" =>
        IO.delay(hub.model.pause()) *> Ok(PauseResponse(true).asJson)

      case POST -> Root / "game" / "resume" =>
        IO.delay(hub.model.resume()) *> Ok(PauseResponse(false).asJson)

      case GET -> Root / "game" / "fen" =>
        IO.delay(hub.model.exportFen()).flatMap(f => Ok(FenResponse(f).asJson))

      case req @ POST -> Root / "game" / "fen" =>
        req.as[FenRequest].flatMap { body =>
          IO.delay(hub.importFen(body.fen)).flatMap {
            case Right(_)  => currentState(hub).flatMap(Ok(_))
            case Left(e)   => BadRequest(err(e))
          }
        }

      case GET -> Root / "game" / "pgn" =>
        IO.delay(hub.model.exportPgn()).flatMap(p => Ok(PgnResponse(p).asJson))

      case req @ POST -> Root / "game" / "pgn" =>
        req.as[PgnRequest].flatMap { body =>
          IO.delay(hub.importPgn(body.pgn)).flatMap {
            case Right(_) => currentState(hub).flatMap(Ok(_))
            case Left(e)  => BadRequest(err(e))
          }
        }

      case GET -> Root / "game" / "moves" =>
        IO.delay(hub.model.state.game.current).flatMap { sit =>
          Ok(ModelBridge.toLegalMovesResponse(sit).asJson)
        }

      case GET -> Root / "game" / "moves" / square =>
        IO.delay(hub.model.state.game.current).flatMap { sit =>
          ModelBridge.toSquareMovesResponse(square, sit) match
            case Right(resp) => Ok(resp.asJson)
            case Left(e)     => BadRequest(err(e))
        }

  private def currentState(hub: GameHub): IO[GameStateResponse] =
    IO.delay {
      val state = hub.model.state
      ModelBridge.toGameStateResponse(state, hub.model.gameResult(), hub.model.isPausedState, hub.isResigned)
    }

  private def err(msg: String): Json = Json.obj("error" -> msg.asJson)
