package org.maichess.mono.bots

import org.maichess.mono.engine.GameState
import org.maichess.mono.model.Move
import org.maichess.mono.rules.StandardRules

class MinimaxBot(val name: String, depth: Int) extends Bot:
  def chooseMove(state: GameState): Option[Move] =
    Ai.bestMove(StandardRules)(Ai.standardEval)(depth)(state)
