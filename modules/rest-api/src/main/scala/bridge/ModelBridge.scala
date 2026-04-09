package org.maichess.mono.api.bridge

import org.maichess.mono.bots.{Bot, BotRegistry}
import org.maichess.mono.engine.{DrawReason, GameResult}
import org.maichess.mono.model.*
import org.maichess.mono.rules.{Situation, StandardRules}
import org.maichess.mono.uifx.{ClockState, SharedGameModel}
import org.maichess.mono.api.model.*

object ModelBridge:

  def toGameStateResponse(
    state:    SharedGameModel.State,
    result:   Option[GameResult],
    paused:   Boolean,
    resigned: Boolean
  ): GameStateResponse =
    val clockResult = state.clock.flatMap(_.flagged).map { flagged =>
      GameResultResponse("timeout", Some(colorStr(flagged.opposite)), None)
    }
    GameStateResponse(
      board             = toBoardResponse(state.game.current.board),
      turn              = colorStr(state.game.current.turn),
      moveHistory       = state.moveHistory,
      futureMoveHistory = state.futureMoveHistory,
      result            = clockResult.orElse(result.map(toGameResultResponse)),
      paused            = paused,
      resigned          = resigned,
      clock             = state.clock.map(toClockResponse),
      metadata          = MetadataResponse(state.metadata.white, state.metadata.black)
    )

  def decodeMove(req: MoveRequest, sit: Situation): Either[String, Move] =
    for
      from  <- Square.fromAlgebraic(req.from).toRight(s"Invalid square: ${req.from}")
      to    <- Square.fromAlgebraic(req.to).toRight(s"Invalid square: ${req.to}")
      promo <- decodePromotion(req.promotion)
      move  <- StandardRules.legalMoves(sit, from)
                 .find(m => m.to == to && promotionMatches(m, promo))
                 .toRight("Illegal move")
    yield move

  def lookupBot(name: Option[String]): Either[String, Option[Bot]] =
    name match
      case None    => Right(None)
      case Some(n) => BotRegistry.all.find(_.name == n)
                        .map(b => Right(Some(b)))
                        .getOrElse(Left(s"Unknown bot: $n"))

  def toLegalMovesResponse(sit: Situation): LegalMovesResponse =
    val grouped = StandardRules.allLegalMoves(sit).groupBy(_.from)
    LegalMovesResponse(grouped.map { (sq, moves) => sq.toAlgebraic -> toMoveTargets(moves) })

  def toSquareMovesResponse(squareStr: String, sit: Situation): Either[String, SquareMovesResponse] =
    Square.fromAlgebraic(squareStr)
      .toRight(s"Invalid square: $squareStr")
      .map { sq =>
        SquareMovesResponse(squareStr, toMoveTargets(StandardRules.legalMoves(sit, sq)))
      }

  def colorStr(c: Color): String = c.toString.toLowerCase

  private def toBoardResponse(board: Board): Map[String, PieceResponse] =
    board.pieces.map { (sq, piece) =>
      sq.toAlgebraic -> PieceResponse(colorStr(piece.color), piece.pieceType.toString.toLowerCase)
    }

  private def toClockResponse(cs: ClockState): ClockResponse =
    ClockResponse(
      whiteMs   = cs.whiteMs,
      blackMs   = cs.blackMs,
      running   = cs.running,
      activeFor = colorStr(cs.activeFor),
      flagged   = cs.flagged.map(colorStr)
    )

  private def toGameResultResponse(result: GameResult): GameResultResponse =
    result match
      case GameResult.Checkmate(winner) => GameResultResponse("checkmate", Some(colorStr(winner)), None)
      case GameResult.Stalemate         => GameResultResponse("stalemate", None, None)
      case GameResult.Draw(reason)      => GameResultResponse("draw", None, Some(drawReasonStr(reason)))

  private def toMoveTargets(moves: List[Move]): List[MoveTargetResponse] =
    moves.groupBy(_.to).map { (sq, mvs) =>
      val promos = mvs.collect { case NormalMove(_, _, Some(pt)) => pt.toString.toLowerCase }
      MoveTargetResponse(sq.toAlgebraic, promos)
    }.toList

  private def decodePromotion(s: Option[String]): Either[String, Option[PieceType]] =
    s match
      case None              => Right(None)
      case Some("queen")     => Right(Some(PieceType.Queen))
      case Some("rook")      => Right(Some(PieceType.Rook))
      case Some("bishop")    => Right(Some(PieceType.Bishop))
      case Some("knight")    => Right(Some(PieceType.Knight))
      case Some(other)       => Left(s"Unknown promotion piece: $other")

  private def promotionMatches(move: Move, promo: Option[PieceType]): Boolean =
    move match
      case NormalMove(_, _, p) => p == promo
      case _                   => promo.isEmpty

  private def drawReasonStr(r: DrawReason): String = r match
    case DrawReason.FiftyMoveRule        => "fiftyMoveRule"
    case DrawReason.InsufficientMaterial => "insufficientMaterial"
    case DrawReason.ThreefoldRepetition  => "threefoldRepetition"
    case DrawReason.Agreement            => "agreement"
