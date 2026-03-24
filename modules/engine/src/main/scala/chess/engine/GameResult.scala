package chess.engine

import chess.model.Color

/** The terminal outcome of a chess game. */
enum GameResult:
  case Checkmate(winner: Color)
  case Stalemate
  case Draw(reason: DrawReason)

/** The reason a game ended in a draw. */
enum DrawReason:
  case FiftyMoveRule
  case InsufficientMaterial
  case ThreefoldRepetition
  case Agreement
