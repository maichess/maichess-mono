package org.maichess.mono.bots

import org.maichess.mono.model.*
import org.maichess.mono.rules.{RuleSet, Situation}
import org.maichess.mono.engine.GameState

object Ai:

  type Evaluator = (Situation, RuleSet) => Int

  val standardValues: Map[PieceType, Int] = Map(
    PieceType.Pawn   -> 100,
    PieceType.Knight -> 320,
    PieceType.Bishop -> 330,
    PieceType.Rook   -> 500,
    PieceType.Queen  -> 900,
    PieceType.King   -> 20000
  )

  def materialEval(pieceValues: Map[PieceType, Int]): Evaluator =
    (sit, _) =>
      sit.board.pieces.values.foldLeft(0) { (acc, piece) =>
        val value = pieceValues.getOrElse(piece.pieceType, 0)
        if piece.color == Color.White then acc + value else acc - value
      }

  val standardEval: Evaluator = materialEval(standardValues)

  def bestMove(ruleSet: RuleSet)(scorer: Evaluator)(depth: Int)(state: GameState): Option[Move] =
    val sit   = state.current
    val moves = ruleSet.allLegalMoves(sit)
    if moves.isEmpty then None
    else
      val isMaximizing = sit.turn == Color.White
      val scored = moves.map { move =>
        val nextSit = sit.advance(move)
        val score   = minimax(ruleSet, scorer, depth - 1, Int.MinValue, Int.MaxValue, !isMaximizing)(nextSit)
        (move, score)
      }
      pickBest(scored, isMaximizing).map(_._1)

  private def pickBest(scored: List[(Move, Int)], maximizing: Boolean): Option[(Move, Int)] =
    @annotation.tailrec
    def loop(remaining: List[(Move, Int)], best: Option[(Move, Int)]): Option[(Move, Int)] =
      remaining match
        case Nil => best
        case (move, score) :: rest =>
          val isBetter = best.fold(true) { case (_, bestScore) =>
            if maximizing then score > bestScore else score < bestScore
          }
          loop(rest, if isBetter then Some((move, score)) else best)
    loop(scored, None)

  private def minimax(
    ruleSet: RuleSet,
    scorer: Evaluator,
    depth: Int,
    alpha: Int,
    beta: Int,
    maximizing: Boolean
  )(sit: Situation): Int =
    val moves = ruleSet.allLegalMoves(sit)
    if depth == 0 || moves.isEmpty then scorer(sit, ruleSet)
    else if maximizing then searchMax(ruleSet, scorer, depth, alpha, beta, moves, sit)
    else searchMin(ruleSet, scorer, depth, alpha, beta, moves, sit)

  @annotation.tailrec
  private def searchMax(
    ruleSet: RuleSet,
    scorer: Evaluator,
    depth: Int,
    alpha: Int,
    beta: Int,
    moves: List[Move],
    sit: Situation
  ): Int =
    moves match
      case Nil => alpha
      case move :: rest =>
        val nextSit  = sit.advance(move)
        val score    = minimax(ruleSet, scorer, depth - 1, alpha, beta, false)(nextSit)
        val newAlpha = alpha.max(score)
        if newAlpha >= beta then newAlpha
        else searchMax(ruleSet, scorer, depth, newAlpha, beta, rest, sit)

  @annotation.tailrec
  private def searchMin(
    ruleSet: RuleSet,
    scorer: Evaluator,
    depth: Int,
    alpha: Int,
    beta: Int,
    moves: List[Move],
    sit: Situation
  ): Int =
    moves match
      case Nil => beta
      case move :: rest =>
        val nextSit = sit.advance(move)
        val score   = minimax(ruleSet, scorer, depth - 1, alpha, beta, true)(nextSit)
        val newBeta = beta.min(score)
        if alpha >= newBeta then newBeta
        else searchMin(ruleSet, scorer, depth, alpha, newBeta, rest, sit)
