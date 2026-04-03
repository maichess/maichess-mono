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

  /** Steps back one move; returns None if there is no history. */
  def undo(state: GameState): Option[GameState] = state.history match
    case Nil           => None
    case prev :: rest  => Some(GameState(rest, prev, state.current :: state.future))

  /** Re-applies the last undone move; returns None if there is no future. */
  def redo(state: GameState): Option[GameState] = state.future match
    case Nil           => None
    case next :: rest  => Some(GameState(state.current :: state.history, next, rest))

  /** Returns the game result if the game is over, otherwise None. */
  def gameResult(state: GameState): Option[GameResult] =
    val sit = state.current
    if      ruleSet.isCheckmate(sit)            then Some(GameResult.Checkmate(sit.turn.opposite))
    else if ruleSet.isStalemate(sit)            then Some(GameResult.Stalemate)
    else if ruleSet.isFiftyMoveRule(sit)        then Some(GameResult.Draw(DrawReason.FiftyMoveRule))
    else if ruleSet.isInsufficientMaterial(sit) then Some(GameResult.Draw(DrawReason.InsufficientMaterial))
    else if threefoldRepetition(state)          then Some(GameResult.Draw(DrawReason.ThreefoldRepetition))
    else None

  private[engine] def advance(state: GameState, move: Move): GameState =
    GameState(state.current :: state.history, state.current.advance(move), Nil)

  private def threefoldRepetition(state: GameState): Boolean =
    val cur   = state.current
    val count = (cur :: state.history).count(s => s.board == cur.board && s.turn == cur.turn)
    count >= 3
