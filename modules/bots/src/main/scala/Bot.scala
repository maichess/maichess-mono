package org.maichess.mono.bots

import org.maichess.mono.engine.GameState
import org.maichess.mono.model.Move

/** A player that chooses moves automatically. */
trait Bot:
  def name: String
  def chooseMove(state: GameState): Option[Move]
