package chess.rules

import chess.model.*

/**
 * Defines the rules of a chess variant.
 *
 * This trait is the primary extension point: a variant only needs to override
 * the methods where its rules differ from [[StandardRules]].
 */
trait RuleSet:

  /** All pseudo-legal moves for the piece on `square` (ignores leaving king in check). */
  def candidateMoves(situation: Situation, square: Square): List[Move]

  /** Legal moves for the piece on `square`: candidates that don't leave the king in check. */
  def legalMoves(situation: Situation, square: Square): List[Move]

  /** All legal moves available to the player whose turn it is. */
  def allLegalMoves(situation: Situation): List[Move]

  /** Returns true if the current player's king is under attack. */
  def isCheck(situation: Situation): Boolean

  /** Returns true if the current player is checkmated. */
  def isCheckmate(situation: Situation): Boolean

  /** Returns true if the current player has no legal moves and is not in check. */
  def isStalemate(situation: Situation): Boolean

  /** Returns true if neither side has sufficient material to deliver checkmate. */
  def isInsufficientMaterial(situation: Situation): Boolean

  /** Returns true if the fifty-move draw can be claimed (100 half-moves without progress). */
  def isFiftyMoveRule(situation: Situation): Boolean

  /**
   * Applies `move` to `situation` and returns the resulting [[Situation]].
   * Assumes the move is legal.
   */
  def applyMove(situation: Situation, move: Move): Situation
