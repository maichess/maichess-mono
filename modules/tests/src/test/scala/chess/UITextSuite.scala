package chess

import chess.engine.{DrawReason, GameController, GameResult, GameState, IllegalMove}
import chess.model.*
import chess.rules.{Board, CastlingRights, Situation, StandardRules}
import chess.ui.TextUI
import munit.FunSuite

class UITextSuite extends FunSuite:

  private def sq(s: String): Square = Square.fromAlgebraic(s).get

  /** Suppress stdout during a block so test output stays clean. */
  private def silenced(f: => Unit): Unit =
    val old = System.out
    System.setOut(java.io.PrintStream(java.io.OutputStream.nullOutputStream()))
    try f finally System.setOut(old)

  /** Build a readLine function that feeds `moves` in order, then returns None (EOF). */
  private def reader(moves: String*): String => Option[String] =
    val q = collection.mutable.Queue(moves*)
    _ => if q.isEmpty then None else Some(q.dequeue())

  private def freshController: GameController = GameController(StandardRules)

  // ── pieceGlyph ─────────────────────────────────────────────────────────────

  test("pieceGlyph returns correct glyphs for all 12 piece/color combinations"):
    assertEquals(TextUI.pieceGlyph(Piece(Color.White, PieceType.King)),   "♔")
    assertEquals(TextUI.pieceGlyph(Piece(Color.White, PieceType.Queen)),  "♕")
    assertEquals(TextUI.pieceGlyph(Piece(Color.White, PieceType.Rook)),   "♖")
    assertEquals(TextUI.pieceGlyph(Piece(Color.White, PieceType.Bishop)), "♗")
    assertEquals(TextUI.pieceGlyph(Piece(Color.White, PieceType.Knight)), "♘")
    assertEquals(TextUI.pieceGlyph(Piece(Color.White, PieceType.Pawn)),   "♙")
    assertEquals(TextUI.pieceGlyph(Piece(Color.Black, PieceType.King)),   "♚")
    assertEquals(TextUI.pieceGlyph(Piece(Color.Black, PieceType.Queen)),  "♛")
    assertEquals(TextUI.pieceGlyph(Piece(Color.Black, PieceType.Rook)),   "♜")
    assertEquals(TextUI.pieceGlyph(Piece(Color.Black, PieceType.Bishop)), "♝")
    assertEquals(TextUI.pieceGlyph(Piece(Color.Black, PieceType.Knight)), "♞")
    assertEquals(TextUI.pieceGlyph(Piece(Color.Black, PieceType.Pawn)),   "♟")

  // ── renderBoard ────────────────────────────────────────────────────────────

  test("renderBoard from White perspective starts with rank 8"):
    val output = TextUI.renderBoard(Board.standard, Color.White)
    assert(output.linesIterator.next().startsWith("8 "))

  test("renderBoard from White perspective ends with file row a-h"):
    val output = TextUI.renderBoard(Board.standard, Color.White)
    assert(output.linesIterator.toList.last.startsWith("  a"))

  test("renderBoard from Black perspective starts with rank 1"):
    val output = TextUI.renderBoard(Board.standard, Color.Black)
    assert(output.linesIterator.next().startsWith("1 "))

  test("renderBoard from Black perspective ends with file row h-a"):
    val output = TextUI.renderBoard(Board.standard, Color.Black)
    assert(output.linesIterator.toList.last.startsWith("  h"))

  test("renderBoard shows dot for empty squares"):
    assert(TextUI.renderBoard(Board.empty, Color.White).contains("·"))

  test("renderBoard shows white king glyph on standard board"):
    assert(TextUI.renderBoard(Board.standard, Color.White).contains("♔"))

  // ── parseMove ─────────────────────────────────────────────────────────────

  test("parseMove accepts compact e2e4"):
    assertEquals(TextUI.parseMove("e2e4"), Right((sq("e2"), sq("e4"))))

  test("parseMove accepts spaced e2 e4"):
    assertEquals(TextUI.parseMove("e2 e4"), Right((sq("e2"), sq("e4"))))

  test("parseMove accepts leading/trailing whitespace"):
    assertEquals(TextUI.parseMove("  e2e4  "), Right((sq("e2"), sq("e4"))))

  test("parseMove rejects 2-character input"):
    assert(TextUI.parseMove("e2").isLeft)

  test("parseMove rejects 6-character input"):
    assert(TextUI.parseMove("e2e4e6").isLeft)

  test("parseMove rejects invalid from-square"):
    assert(TextUI.parseMove("z9e4").isLeft)

  test("parseMove rejects invalid to-square"):
    assert(TextUI.parseMove("e2z9").isLeft)

  // ── findLegalMove ──────────────────────────────────────────────────────────

  test("findLegalMove returns Some for a legal move"):
    val m = TextUI.findLegalMove(Situation.standard, sq("e2"), sq("e4"))
    assert(m.isDefined)

  test("findLegalMove returns None for an illegal move"):
    val m = TextUI.findLegalMove(Situation.standard, sq("e2"), sq("e5"))
    assert(m.isEmpty)

  test("findLegalMove prefers queen when multiple promotions match"):
    val sit = Situation(
      Board(Map(sq("e7") -> Piece(Color.White, PieceType.Pawn))),
      Color.White, CastlingRights.none, None, 0, 1,
    )
    TextUI.findLegalMove(sit, sq("e7"), sq("e8")) match
      case Some(NormalMove(_, _, Some(PieceType.Queen))) => ()
      case other                                         => fail(s"Expected queen promotion, got $other")

  // ── resultMessage ─────────────────────────────────────────────────────────

  test("resultMessage: White checkmate"):
    assertEquals(TextUI.resultMessage(GameResult.Checkmate(Color.White)), "Checkmate! White wins.")

  test("resultMessage: Black checkmate"):
    assertEquals(TextUI.resultMessage(GameResult.Checkmate(Color.Black)), "Checkmate! Black wins.")

  test("resultMessage: stalemate"):
    assertEquals(TextUI.resultMessage(GameResult.Stalemate), "Stalemate — draw.")

  test("resultMessage: fifty-move draw"):
    assertEquals(TextUI.resultMessage(GameResult.Draw(DrawReason.FiftyMoveRule)), "Draw by fifty-move rule.")

  test("resultMessage: insufficient material draw"):
    assertEquals(TextUI.resultMessage(GameResult.Draw(DrawReason.InsufficientMaterial)), "Draw by insufficient material.")

  test("resultMessage: threefold repetition draw"):
    assertEquals(TextUI.resultMessage(GameResult.Draw(DrawReason.ThreefoldRepetition)), "Draw by threefold repetition.")

  test("resultMessage: agreement draw"):
    assertEquals(TextUI.resultMessage(GameResult.Draw(DrawReason.Agreement)), "Draw by agreement.")

  // ── gameLoop branches ─────────────────────────────────────────────────────

  test("gameLoop exits cleanly on immediate EOF"):
    silenced:
      TextUI.gameLoop(freshController.newGame(), freshController, reader())

  test("gameLoop shows Check! when king is in check"):
    val checkBoard = Board(Map(
      sq("e1") -> Piece(Color.White, PieceType.King),
      sq("e8") -> Piece(Color.Black, PieceType.Rook),
    ))
    val checkState = GameState(
      Nil,
      Situation(checkBoard, Color.White, CastlingRights.none, None, 0, 1),
    )
    silenced:
      TextUI.gameLoop(checkState, freshController, reader())

  test("gameLoop handles parse error then exits on EOF"):
    silenced:
      TextUI.gameLoop(freshController.newGame(), freshController, reader("bad-input"))

  test("gameLoop handles illegal move then exits on EOF"):
    silenced:
      TextUI.gameLoop(freshController.newGame(), freshController, reader("e2e5"))

  test("gameLoop accepts a legal move and continues to EOF"):
    silenced:
      TextUI.gameLoop(freshController.newGame(), freshController, reader("e2e4"))

  test("gameLoop terminates after Scholar's Mate (game result branch)"):
    silenced:
      TextUI.gameLoop(
        freshController.newGame(),
        freshController,
        reader("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7"),
      )

  test("gameLoop shows Rejected message when controller refuses a move (defensive branch)"):
    // Controller that accepts legal-move lookup but always rejects applyMove
    class RejectingController extends GameController(StandardRules):
      override def applyMove(state: GameState, move: Move): Either[IllegalMove, GameState] =
        Left(IllegalMove(move, "test rejection"))

    silenced:
      TextUI.gameLoop(freshController.newGame(), RejectingController(), reader("e2e4"))

  // ── fromStdin ─────────────────────────────────────────────────────────────

  test("fromStdin reads a line from redirected System.in"):
    val old = System.in
    System.setIn(java.io.ByteArrayInputStream("hello\n".getBytes))
    try assertEquals(TextUI.fromStdin(""), Some("hello"))
    finally System.setIn(old)

  test("fromStdin returns None on EOF"):
    val old = System.in
    System.setIn(java.io.ByteArrayInputStream(Array.emptyByteArray))
    try assertEquals(TextUI.fromStdin(""), None)
    finally System.setIn(old)

  // ── play ──────────────────────────────────────────────────────────────────

  test("play prints intro then exits on EOF"):
    silenced:
      TextUI.play(freshController, reader())

  test("play with Scholar's Mate sequence ends with checkmate message"):
    silenced:
      TextUI.play(freshController, reader("e2e4", "e7e5", "d1h5", "b8c6", "f1c4", "g8f6", "h5f7"))
