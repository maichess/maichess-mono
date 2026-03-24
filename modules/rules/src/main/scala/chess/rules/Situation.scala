package chess.rules

import chess.model.*

/**
 * The complete state of a chess game at a single moment in time.
 *
 * Placed in the `rules` module because [[RuleSet]] methods operate on it
 * directly, and [[Board]] (also in `rules`) is a field.
 */
case class Situation(
  board: Board,
  turn: Color,
  castlingRights: CastlingRights,
  enPassantSquare: Option[Square],
  halfMoveClock: Int,
  fullMoveNumber: Int,
)

object Situation:
  /** A situation in the standard chess starting position. */
  val standard: Situation = Situation(
    board           = Board.standard,
    turn            = Color.White,
    castlingRights  = CastlingRights.all,
    enPassantSquare = None,
    halfMoveClock   = 0,
    fullMoveNumber  = 1,
  )
