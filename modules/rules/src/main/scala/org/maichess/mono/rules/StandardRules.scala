package org.maichess.mono.rules

import org.maichess.mono.model.*

/** Placeholder stub — full implementation in Task 7. */
object StandardRules extends RuleSet:
  def candidateMoves(situation: Situation, square: Square): List[Move]  = Nil
  def legalMoves(situation: Situation, square: Square): List[Move]      = Nil
  def allLegalMoves(situation: Situation): List[Move]                   = Nil
  def isCheck(situation: Situation): Boolean                            = false
  def isCheckmate(situation: Situation): Boolean                        = false
  def isStalemate(situation: Situation): Boolean                        = false
  def isInsufficientMaterial(situation: Situation): Boolean             = false
  def isFiftyMoveRule(situation: Situation): Boolean                    = false
