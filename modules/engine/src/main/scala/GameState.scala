package org.maichess.mono.engine

import org.maichess.mono.rules.Situation

/** Full game history plus the current situation.
 *  `history` is most-recent-first; `future` holds situations that were undone. */
case class GameState(
  history: List[Situation],
  current: Situation,
  future:  List[Situation]
)

object GameState:
  def apply(history: List[Situation], current: Situation): GameState =
    GameState(history, current, Nil)
