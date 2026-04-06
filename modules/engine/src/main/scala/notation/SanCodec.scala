package org.maichess.mono.engine

import org.maichess.mono.model.Move
import org.maichess.mono.rules.{RuleSet, Situation}

trait SanCodec:
  def encode(situation: Situation, move: Move, after: Situation, ruleSet: RuleSet): String
  def decode(situation: Situation, san: String, ruleSet: RuleSet): Either[String, Move]
