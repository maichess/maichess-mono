package org.maichess.mono.engine

enum DrawReason:
  case FiftyMoveRule, InsufficientMaterial, ThreefoldRepetition, Agreement
