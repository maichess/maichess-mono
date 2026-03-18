package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.*

/** Orchestrates game flow: applying moves, tracking state, detecting game-over. */
class GameController(ruleSet: RuleSet):

  /** Returns a new game in the standard starting position. */
  def newGame(): GameState =
    GameState(history = Nil, current = Situation.standard)

  /** Applies `move` if legal; returns updated state or an error. State is never mutated. */
  def applyMove(state: GameState, move: Move): Either[IllegalMove, GameState] =
    val legal = ruleSet.legalMoves(state.current, move.from)
    if legal.contains(move) then Right(advance(state, move))
    else Left(IllegalMove("Illegal move"))

  /** Returns the game result if the game is over, otherwise None. */
  def gameResult(state: GameState): Option[GameResult] =
    val sit = state.current
    if      ruleSet.isCheckmate(sit)           then Some(GameResult.Checkmate(sit.turn.opposite))
    else if ruleSet.isStalemate(sit)           then Some(GameResult.Stalemate)
    else if ruleSet.isFiftyMoveRule(sit)       then Some(GameResult.Draw(DrawReason.FiftyMoveRule))
    else if ruleSet.isInsufficientMaterial(sit) then Some(GameResult.Draw(DrawReason.InsufficientMaterial))
    else if threefoldRepetition(state)         then Some(GameResult.Draw(DrawReason.ThreefoldRepetition))
    else None

  private def advance(state: GameState, move: Move): GameState =
    val sit  = state.current
    val next = Situation(
      board           = sit.board.applyMove(move),
      turn            = sit.turn.opposite,
      castlingRights  = updatedCastling(sit, move),
      enPassantSquare = enPassantAfter(sit, move),
      halfMoveClock   = halfClockAfter(sit, move),
      fullMoveNumber  = if sit.turn == Color.Black then sit.fullMoveNumber + 1
                        else sit.fullMoveNumber
    )
    GameState(sit :: state.history, next)

  private def enPassantAfter(sit: Situation, move: Move): Option[Square] = move match
    case NormalMove(from, to, None) =>
      val rankDiff       = to.rank.toInt - from.rank.toInt
      val isPawnDoublePush = sit.board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
                             (rankDiff == 2 || rankDiff == -2)
      Option.when(isPawnDoublePush)(from.offset(0, rankDiff / 2)).flatten
    case _ => None

  private def halfClockAfter(sit: Situation, move: Move): Int =
    val isPawn    = sit.board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn)
    val isCapture = sit.board.pieceAt(move.to).isDefined || isEnPassant(move)
    if isPawn || isCapture then 0 else sit.halfMoveClock + 1

  private def isEnPassant(move: Move): Boolean = move match
    case _: EnPassantMove => true
    case _                => false

  private def updatedCastling(sit: Situation, move: Move): CastlingRights =
    val cr = sit.castlingRights
    sit.board.pieceAt(move.from) match
      case Some(Piece(Color.White, PieceType.King)) =>
        cr.copy(whiteKingSide = false, whiteQueenSide = false)
      case Some(Piece(Color.Black, PieceType.King)) =>
        cr.copy(blackKingSide = false, blackQueenSide = false)
      case Some(Piece(Color.White, PieceType.Rook)) =>
        move.from.toAlgebraic match
          case "a1" => cr.copy(whiteQueenSide = false)
          case "h1" => cr.copy(whiteKingSide  = false)
          case _    => cr
      case Some(Piece(Color.Black, PieceType.Rook)) =>
        move.from.toAlgebraic match
          case "a8" => cr.copy(blackQueenSide = false)
          case "h8" => cr.copy(blackKingSide  = false)
          case _    => cr
      case _ => cr

  private def threefoldRepetition(state: GameState): Boolean =
    val cur   = state.current
    val count = (cur :: state.history).count(s => s.board == cur.board && s.turn == cur.turn)
    count >= 3
