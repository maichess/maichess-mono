package org.maichess.mono.bots.engine

import org.maichess.mono.bots.Bot
import org.maichess.mono.engine.GameState
import org.maichess.mono.model.Move

// ── UciBot ────────────────────────────────────────────────────────────────────
// Stub that wires the new bitboard engine into the Bot interface.
// chooseMove currently returns None (no search implemented yet).
// Move generation and alpha-beta search will be added in subsequent steps.
class UciBot extends Bot:
  val name: String = "UCI Engine (WIP)"

  def chooseMove(state: GameState): Option[Move] = None
