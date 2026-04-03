package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.bots.{Ai, Bot, BotRegistry, MinimaxBot}
import org.maichess.mono.engine.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.*

class AiSuite extends FunSuite:

  val ctrl = GameController(StandardRules)

  def sq(alg: String): Square =
    Square.fromAlgebraic(alg).getOrElse(throw new AssertionError(s"Invalid square: $alg"))
  def mv(from: String, to: String): NormalMove = NormalMove(sq(from), sq(to))

  def applyMoves(state: GameState, moves: List[Move]): GameState =
    moves.foldLeft(state) { (s, m) =>
      ctrl.applyMove(s, m).getOrElse(throw new AssertionError(s"Illegal move: $m"))
    }

  // ── standardValues & materialEval ─────────────────────────────────────────

  test("standardValues: pawn = 100"):
    assertEquals(Ai.standardValues(PieceType.Pawn), 100)

  test("standardValues: queen = 900"):
    assertEquals(Ai.standardValues(PieceType.Queen), 900)

  test("materialEval with empty map returns 0 for standard position"):
    val sit   = Situation.standard
    val score = Ai.materialEval(Map.empty)(sit, StandardRules)
    assertEquals(score, 0)

  test("materialEval is symmetric for starting position"):
    val sit   = Situation.standard
    val score = Ai.standardEval(sit, StandardRules)
    assertEquals(score, 0)

  test("materialEval favors side with more material"):
    val sit = Situation.standard
    val boardNoWhiteQueen = sit.board.pieces
      .filter { case (_, p) => !(p.color == Color.White && p.pieceType == PieceType.Queen) }
    val reducedBoard = Board(boardNoWhiteQueen)
    val reducedSit   = sit.copy(board = reducedBoard)
    val score        = Ai.standardEval(reducedSit, StandardRules)
    assert(score < 0, s"Expected score < 0 but got $score")

  // ── bestMove: no moves (game over) ────────────────────────────────────────

  test("bestMove returns None when no legal moves (stalemate)"):
    val pieces = Map(
      sq("a8") -> Piece(Color.Black, PieceType.King),
      sq("b6") -> Piece(Color.White, PieceType.Queen),
      sq("c6") -> Piece(Color.White, PieceType.King)
    )
    val sit   = Situation(Board(pieces), Color.Black, CastlingRights.none, None, 0, 1)
    val state = GameState(Nil, sit)
    val result = Ai.bestMove(StandardRules)(Ai.standardEval)(2)(state)
    assertEquals(result, None)

  // ── bestMove: finds a move in a real position ──────────────────────────────

  test("bestMove returns Some move from starting position (depth 1, White)"):
    val state  = ctrl.newGame()
    val result = Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state)
    assert(result.isDefined)

  test("bestMove returns Some move from starting position (depth 1, Black)"):
    val state0 = ctrl.newGame()
    val state1 = ctrl.applyMove(state0, mv("e2", "e4")).getOrElse(throw AssertionError(""))
    val result = Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state1)
    assert(result.isDefined)

  // ── bestMove: prefers capture (depth 1) ───────────────────────────────────

  test("bestMove at depth 1 captures a free queen"):
    val pieces = Map(
      sq("e8") -> Piece(Color.Black, PieceType.King),
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e5") -> Piece(Color.White, PieceType.Queen),
      sq("a5") -> Piece(Color.Black, PieceType.Queen)
    )
    val sit    = Situation(Board(pieces), Color.Black, CastlingRights.none, None, 0, 1)
    val state  = GameState(Nil, sit)
    val chosen = Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state)
      .getOrElse(throw AssertionError("No move returned"))
    assertEquals(chosen.to, sq("e5"))

  // ── alpha-beta pruning paths ───────────────────────────────────────────────

  test("bestMove at depth 2 returns a valid legal move"):
    val state  = ctrl.newGame()
    val chosen = Ai.bestMove(StandardRules)(Ai.standardEval)(2)(state)
      .getOrElse(throw AssertionError("No move"))
    val legal  = StandardRules.allLegalMoves(state.current)
    assert(legal.contains(chosen), s"$chosen is not a legal move")

  // depth 3 exercises searchMax and alpha-beta cutoffs
  test("bestMove at depth 3 returns a valid legal move"):
    val state  = ctrl.newGame()
    val chosen = Ai.bestMove(StandardRules)(Ai.standardEval)(3)(state)
      .getOrElse(throw AssertionError("No move"))
    val legal  = StandardRules.allLegalMoves(state.current)
    assert(legal.contains(chosen), s"$chosen is not a legal move")

  // position with material asymmetry to trigger alpha-beta cutoffs
  test("bestMove at depth 3 in tactical position exercises alpha-beta cutoff"):
    // White queen can fork or capture; unbalanced material triggers pruning
    val pieces = Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.King),
      sq("d1") -> Piece(Color.White, PieceType.Queen),
      sq("d8") -> Piece(Color.Black, PieceType.Queen),
      sq("a1") -> Piece(Color.White, PieceType.Rook),
      sq("a8") -> Piece(Color.Black, PieceType.Rook),
      sq("h1") -> Piece(Color.White, PieceType.Rook),
      sq("h8") -> Piece(Color.Black, PieceType.Rook)
    )
    val sit   = Situation(Board(pieces), Color.White, CastlingRights.none, None, 0, 1)
    val state = GameState(Nil, sit)
    val result = Ai.bestMove(StandardRules)(Ai.standardEval)(3)(state)
    assert(result.isDefined)

  // Black-to-move at depth 3: pickBest minimizing branch
  test("bestMove at depth 3 Black to move exercises minimizing pickBest"):
    val state0 = ctrl.newGame()
    val state1 = ctrl.applyMove(state0, mv("e2", "e4")).getOrElse(throw AssertionError(""))
    val chosen = Ai.bestMove(StandardRules)(Ai.standardEval)(3)(state1)
      .getOrElse(throw AssertionError("No move"))
    assert(StandardRules.allLegalMoves(state1.current).contains(chosen))

  // ── enPassant path ─────────────────────────────────────────────────────────

  test("bestMove handles en passant position without error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("e2", "e4"), mv("a7", "a5"),
      mv("e4", "e5"), mv("d7", "d5")
    ))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state0).isDefined)

  // ── castling rights updates ────────────────────────────────────────────────

  test("bestMove after white castles — no error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("e2", "e4"), mv("e7", "e5"),
      mv("g1", "f3"), mv("g8", "f6"),
      mv("f1", "c4"), mv("f8", "c5")
    ))
    val castleMove = CastlingMove(sq("e1"), sq("g1"), sq("h1"), sq("f1"))
    val state1     = ctrl.applyMove(state0, castleMove).getOrElse(throw AssertionError(""))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state1).isDefined)

  // ── rook moves update castling rights ─────────────────────────────────────

  test("bestMove after white a1-rook move — no error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("a2", "a4"), mv("a7", "a5"),
      mv("a1", "a3"), mv("h7", "h6")
    ))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state0).isDefined)

  test("bestMove after white h1-rook move — no error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("h2", "h4"), mv("h7", "h5"),
      mv("h1", "h3"), mv("a7", "a6")
    ))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state0).isDefined)

  test("bestMove after black a8-rook move — no error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("a2", "a4"), mv("a7", "a5"),
      mv("b2", "b4"), mv("a8", "a6"),
      mv("c2", "c4"), mv("h7", "h5")
    ))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state0).isDefined)

  test("bestMove after black h8-rook move — no error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("h2", "h4"), mv("h7", "h5"),
      mv("a2", "a4"), mv("h8", "h6"),
      mv("b2", "b4"), mv("a7", "a5")
    ))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state0).isDefined)

  // ── halfMoveClock ─────────────────────────────────────────────────────────

  test("bestMove after long non-capture sequence — halfMoveClock increments, no error"):
    val state0 = applyMoves(ctrl.newGame(), List(
      mv("g1", "f3"), mv("g8", "f6"),
      mv("f3", "g1"), mv("f6", "g8"),
      mv("g1", "f3"), mv("g8", "f6")
    ))
    assert(Ai.bestMove(StandardRules)(Ai.standardEval)(1)(state0).isDefined)

  // ── Bot trait ─────────────────────────────────────────────────────────────

  test("MinimaxBot implements Bot trait"):
    val bot: Bot = new MinimaxBot("Test", 1)
    assertEquals(bot.name, "Test")

  test("MinimaxBot.chooseMove returns a legal move from start"):
    val bot    = new MinimaxBot("Test", 1)
    val state  = ctrl.newGame()
    val chosen = bot.chooseMove(state).getOrElse(throw AssertionError("No move"))
    assert(StandardRules.allLegalMoves(state.current).contains(chosen))

  test("MinimaxBot.chooseMove returns None when no legal moves"):
    val pieces = Map(
      sq("a8") -> Piece(Color.Black, PieceType.King),
      sq("b6") -> Piece(Color.White, PieceType.Queen),
      sq("c6") -> Piece(Color.White, PieceType.King)
    )
    val sit   = Situation(Board(pieces), Color.Black, CastlingRights.none, None, 0, 1)
    val state = GameState(Nil, sit)
    assertEquals(new MinimaxBot("Test", 1).chooseMove(state), None)

  // ── BotRegistry ───────────────────────────────────────────────────────────

  test("BotRegistry.all is non-empty"):
    assert(BotRegistry.all.nonEmpty)

  test("BotRegistry.all bots have distinct names"):
    val names = BotRegistry.all.map(_.name)
    assertEquals(names.distinct.length, names.length)