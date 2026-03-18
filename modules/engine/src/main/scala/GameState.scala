package org.maichess.mono.engine

import org.maichess.mono.rules.Situation

/** Full game history plus the current situation. */
case class GameState(history: List[Situation], current: Situation)
