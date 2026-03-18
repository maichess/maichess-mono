package org.maichess.mono.rules

import org.maichess.mono.model.*

/** Extension point for chess rule variants. Implement to support Chess960, etc. */
trait RuleSet:
  /** All pseudo-legal moves for the piece on `square` (ignores check). */
  def candidateMoves(situation: Situation, square: Square): List[Move]

  /** Legal moves for `square`: candidates that don't leave own king in check. */
  def legalMoves(situation: Situation, square: Square): List[Move]

  /** All legal moves for the side to move. */
  def allLegalMoves(situation: Situation): List[Move]

  /** True if the side to move's king is in check. */
  def isCheck(situation: Situation): Boolean

  /** True if the side to move is in check and has no legal moves. */
  def isCheckmate(situation: Situation): Boolean

  /** True if the side to move is not in check and has no legal moves. */
  def isStalemate(situation: Situation): Boolean

  /** True if neither side has enough material to checkmate. */
  def isInsufficientMaterial(situation: Situation): Boolean

  /** True if halfMoveClock >= 100 (50-move rule). */
  def isFiftyMoveRule(situation: Situation): Boolean
