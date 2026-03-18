package org.maichess.mono.rules

import org.maichess.mono.model.*

/** Placeholder stub — full implementation in Task 7. */
object StandardRules:
  def isCheckmate(s: Situation): Boolean   = false
  def isStalemate(s: Situation): Boolean   = false
  def isFiftyMoveRule(s: Situation): Boolean = false
  def legalMoves(s: Situation, from: Square): List[Move]     = Nil
  def candidateMoves(s: Situation, from: Square): List[Move] = Nil
