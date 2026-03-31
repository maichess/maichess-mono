package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.{CastlingRights, RuleSet, Situation}

object Ai:

  type Evaluator    = (Situation, RuleSet) => Int
  type MoveProvider = GameState => Option[Move]

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

  def bestMove(ruleSet: RuleSet)(scorer: Evaluator)(depth: Int): MoveProvider =
    state =>
      val sit   = state.current
      val moves = ruleSet.allLegalMoves(sit)
      if moves.isEmpty then None
      else
        val isMaximizing = sit.turn == Color.White
        val scored = moves.map { move =>
          val nextSit = applySitMove(sit, move)
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

  private def applySitMove(sit: Situation, move: Move): Situation =
    Situation(
      board           = sit.board.applyMove(move),
      turn            = sit.turn.opposite,
      castlingRights  = updatedCastling(sit, move),
      enPassantSquare = enPassantAfter(sit, move),
      halfMoveClock   = halfClockAfter(sit, move),
      fullMoveNumber  = if sit.turn == Color.Black then sit.fullMoveNumber + 1 else sit.fullMoveNumber
    )

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
        val nextSit  = applySitMove(sit, move)
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
        val nextSit = applySitMove(sit, move)
        val score   = minimax(ruleSet, scorer, depth - 1, alpha, beta, true)(nextSit)
        val newBeta = beta.min(score)
        if alpha >= newBeta then newBeta
        else searchMin(ruleSet, scorer, depth, alpha, newBeta, rest, sit)

  private def enPassantAfter(sit: Situation, move: Move): Option[Square] = move match
    case NormalMove(from, to, None) =>
      val rankDiff         = to.rank.toInt - from.rank.toInt
      val isPawnDoublePush = sit.board.pieceAt(from).exists(_.pieceType == PieceType.Pawn) &&
                             (rankDiff == 2 || rankDiff == -2)
      Option.when(isPawnDoublePush)(from.offset(0, rankDiff / 2)).flatten
    case _ => None

  private def isEnPassant(move: Move): Boolean = move match
    case _: EnPassantMove => true
    case _                => false

  private def halfClockAfter(sit: Situation, move: Move): Int =
    val isPawn    = sit.board.pieceAt(move.from).exists(_.pieceType == PieceType.Pawn)
    val isCapture = sit.board.pieceAt(move.to).isDefined || isEnPassant(move)
    if isPawn || isCapture then 0 else sit.halfMoveClock + 1

  private def updatedCastling(sit: Situation, move: Move): CastlingRights =
    val cr = sit.castlingRights
    sit.board.pieceAt(move.from) match
      case Some(Piece(Color.White, PieceType.King)) =>
        cr.copy(whiteKingSide = false, whiteQueenSide = false)
      case Some(Piece(Color.Black, PieceType.King)) =>
        cr.copy(blackKingSide = false, blackQueenSide = false)
      case Some(Piece(Color.White, PieceType.Rook)) =>
        move.from.toAlgebraic match
          case "a1" => cr.copy(whiteQueenSide = false)
          case "h1" => cr.copy(whiteKingSide  = false)
          case _    => cr
      case Some(Piece(Color.Black, PieceType.Rook)) =>
        move.from.toAlgebraic match
          case "a8" => cr.copy(blackQueenSide = false)
          case "h8" => cr.copy(blackKingSide  = false)
          case _    => cr
      case _ => cr
