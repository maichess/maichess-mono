package org.maichess.mono.engine

import org.maichess.mono.model.Color

enum GameResult:
  case Checkmate(winner: Color)
  case Stalemate
  case Draw(reason: DrawReason)
