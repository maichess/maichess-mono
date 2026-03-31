package org.maichess.mono.rules

import org.maichess.mono.model.*

/** Complete game state needed for rule evaluation. */
case class Situation(
  board:           Board,
  turn:            Color,
  castlingRights:  CastlingRights,
  enPassantSquare: Option[Square],
  halfMoveClock:   Int,
  fullMoveNumber:  Int
):
  /** Returns the situation after applying `move` (does not validate legality). */
  def advance(move: Move): Situation =
    Situation(
      board           = board.applyMove(move),
      turn            = turn.opposite,
      castlingRights  = updatedCastling(move),
      enPassantSquare = enPassantAfter(move),
      halfMoveClock   = halfClockAfter(move),
      fullMoveNumber  = if turn == Color.Black then fullMoveNumber + 1 else fullMoveNumber
    )

  private def updatedCastling(move: Move): CastlingRights =
    board.pieceAt(move.from) match
      case Some(Piece(Color.White, PieceType.King)) =>
        castlingRights.copy(whiteKingSide = false, whiteQueenSide = false)
      case Some(Piece(Color.Black, PieceType.King)) =>
        castlingRights.copy(blackKingSide = false, blackQueenSide = false)
      case Some(Piece(Color.White, PieceType.Rook)) =>
        move.from.toAlgebraic match
          case "a1" => castlingRights.copy(whiteQueenSide = false)
          case "h1" => castlingRights.copy(whiteKingSide  = false)
          case _    => castlingRights
      case Some(Piece(Color.Black, PieceType.Rook)) =>
        move.from.toAlgebraic match
          case "a8" => castlingRights.copy(blackQueenSide = false)
          case "h8" => castlingRights.copy(blackKingSide  = false)
          case _    => castlingRights
      case _ => castlingRights

  private def enPassantAfter(move: Move): Option[Square] = move match
    case NormalMove(from, to, None) =>
      val rankDiff         = to.rank.toInt - from.rank.toInt
      val isPawnDoublePush = board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
                             (rankDiff == 2 || rankDiff == -2)
      Option.when(isPawnDoublePush)(from.offset(0, rankDiff / 2)).flatten
    case _ => None

  private def halfClockAfter(move: Move): Int =
    val isPawn    = board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn)
    val isCapture = board.pieceAt(move.to).isDefined || isEnPassant(move)
    if isPawn || isCapture then 0 else halfMoveClock + 1

  private def isEnPassant(move: Move): Boolean = move match
    case _: EnPassantMove => true
    case _                => false

object Situation:
  /** Returns the standard chess starting situation. */
  def standard: Situation = Situation(
    board           = Board.standard,
    turn            = Color.White,
    castlingRights  = CastlingRights.all,
    enPassantSquare = None,
    halfMoveClock   = 0,
    fullMoveNumber  = 1
  )
