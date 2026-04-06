package org.maichess.mono.engine

import org.maichess.mono.rules.RuleSet

trait PgnCodec:
  def encode(state: GameState, ruleSet: RuleSet, metadata: PgnMetadata): String
  def decode(pgn: String, ruleSet: RuleSet): Either[String, (GameState, PgnMetadata)]
