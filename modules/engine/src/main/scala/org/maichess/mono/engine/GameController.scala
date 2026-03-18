package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.*

/** Placeholder stub — full implementation in Task 8. */

case class IllegalMove(reason: String)

case class GameState(history: List[Situation], current: Situation)

enum GameResult:
  case Checkmate(winner: Color)
  case Stalemate
  case Draw

case class GameController(rules: StandardRules.type):
  def newGame(): GameState = GameState(Nil, Situation.standard)

  def applyMove(state: GameState, move: Move): Either[IllegalMove, GameState] =
    Left(IllegalMove("not implemented"))

  def gameResult(state: GameState): Option[GameResult] = None
