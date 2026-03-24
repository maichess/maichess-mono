package chess.engine

import chess.rules.Situation

/**
 * The full mutable-free game state: the current position plus all prior positions.
 *
 * @param history All situations before the current one, most recent first.
 *                Used for threefold-repetition detection.
 * @param current The live situation that the next move will be applied to.
 */
case class GameState(
  history: List[Situation],
  current: Situation,
)
