package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*
import com.googlecode.lanterna.TextColor
import org.maichess.mono.ui.{CursorState, Direction, UIState, BoardComponent, SquareHighlights}
import org.maichess.mono.ui.SidePanel
import org.maichess.mono.model.{NormalMove, CastlingMove, EnPassantMove}

class TextUISuite extends FunSuite:

  private def sq(s: String): Square  = Square.fromAlgebraic(s).get
  private def mv(f: String, t: String): Move = NormalMove(sq(f), sq(t))

  // ── cursorSquare ────────────────────────────────────────────────────────────

  test("cursorSquare returns cursor when Navigating"):
    assertEquals(UIState.cursorSquare(CursorState.Navigating(sq("e2"))), sq("e2"))

  test("cursorSquare returns current target square when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"), mv("e2", "e3"))
    assertEquals(
      UIState.cursorSquare(CursorState.PieceSelected(sq("e2"), 1, targets)),
      sq("e3")
    )

  // ── selectedSquare ──────────────────────────────────────────────────────────

  test("selectedSquare is None when Navigating"):
    assertEquals(UIState.selectedSquare(CursorState.Navigating(sq("e2"))), None)

  test("selectedSquare is Some(from) when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"))
    assertEquals(
      UIState.selectedSquare(CursorState.PieceSelected(sq("e2"), 0, targets)),
      Some(sq("e2"))
    )

  // ── targetSquares ───────────────────────────────────────────────────────────

  test("targetSquares is empty when Navigating"):
    assertEquals(UIState.targetSquares(CursorState.Navigating(sq("e2"))), Set.empty[Square])

  test("targetSquares returns all target squares when PieceSelected"):
    val targets = IndexedSeq(mv("e2", "e4"), mv("e2", "e3"))
    assertEquals(
      UIState.targetSquares(CursorState.PieceSelected(sq("e2"), 0, targets)),
      Set(sq("e4"), sq("e3"))
    )

  // ── moveCursorFree ──────────────────────────────────────────────────────────

  test("moveCursorFree Up increases rank"):
    assertEquals(UIState.moveCursorFree(sq("e2"), Direction.Up), sq("e3"))

  test("moveCursorFree Down decreases rank"):
    assertEquals(UIState.moveCursorFree(sq("e3"), Direction.Down), sq("e2"))

  test("moveCursorFree Right increases file"):
    assertEquals(UIState.moveCursorFree(sq("e2"), Direction.Right), sq("f2"))

  test("moveCursorFree Left decreases file"):
    assertEquals(UIState.moveCursorFree(sq("e2"), Direction.Left), sq("d2"))

  test("moveCursorFree Up wraps from rank 8 to rank 1"):
    assertEquals(UIState.moveCursorFree(sq("e8"), Direction.Up), sq("e1"))

  test("moveCursorFree Down wraps from rank 1 to rank 8"):
    assertEquals(UIState.moveCursorFree(sq("e1"), Direction.Down), sq("e8"))

  test("moveCursorFree Right wraps from h-file to a-file"):
    assertEquals(UIState.moveCursorFree(sq("h4"), Direction.Right), sq("a4"))

  test("moveCursorFree Left wraps from a-file to h-file"):
    assertEquals(UIState.moveCursorFree(sq("a4"), Direction.Left), sq("h4"))

  // ── moveCursorTargets ───────────────────────────────────────────────────────

  test("moveCursorTargets Down advances index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(UIState.moveCursorTargets(targets, 0, Direction.Down), 1)

  test("moveCursorTargets Up retreats index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(UIState.moveCursorTargets(targets, 1, Direction.Up), 0)

  test("moveCursorTargets Down wraps from last to first"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(UIState.moveCursorTargets(targets, 1, Direction.Down), 0)

  test("moveCursorTargets Up wraps from first to last"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(UIState.moveCursorTargets(targets, 0, Direction.Up), 1)

  test("moveCursorTargets Right advances index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(UIState.moveCursorTargets(targets, 0, Direction.Right), 1)

  test("moveCursorTargets Left retreats index"):
    val targets = IndexedSeq(mv("e2","e3"), mv("e2","e4"))
    assertEquals(UIState.moveCursorTargets(targets, 1, Direction.Left), 0)

  // ── renderSquare ─────────────────────────────────────────────────────────────

  private def noHl: SquareHighlights = SquareHighlights(None, None, Set.empty)

  test("renderSquare: a1 is dark (file=0 + rank=0 = 0, even)"):
    val tc = BoardComponent.renderSquare(Board.empty, sq("a1"), noHl)
    assertEquals(tc.getBackgroundColor, new TextColor.RGB(181, 136, 99))

  test("renderSquare: b1 is light (file=1 + rank=0 = 1, odd)"):
    val tc = BoardComponent.renderSquare(Board.empty, sq("b1"), noHl)
    assertEquals(tc.getBackgroundColor, new TextColor.RGB(240, 217, 181))

  test("renderSquare: cursor square has yellow background"):
    val hl = SquareHighlights(cursor = Some(sq("e4")), None, Set.empty)
    val tc = BoardComponent.renderSquare(Board.empty, sq("e4"), hl)
    assertEquals(tc.getBackgroundColor, new TextColor.RGB(247, 247, 105))

  test("renderSquare: selected square has green background"):
    val hl = SquareHighlights(None, selected = Some(sq("e2")), Set.empty)
    val tc = BoardComponent.renderSquare(Board.empty, sq("e2"), hl)
    assertEquals(tc.getBackgroundColor, new TextColor.RGB(130, 151, 105))

  test("renderSquare: target square has blue background"):
    val hl = SquareHighlights(None, None, targets = Set(sq("e4")))
    val tc = BoardComponent.renderSquare(Board.empty, sq("e4"), hl)
    assertEquals(tc.getBackgroundColor, new TextColor.RGB(100, 130, 180))

  test("renderSquare: white king on e1 shows \u2654"):
    val tc = BoardComponent.renderSquare(Board.standard, sq("e1"), noHl)
    assertEquals(tc.getCharacter, '\u2654')

  test("renderSquare: black queen on d8 shows \u265B"):
    val tc = BoardComponent.renderSquare(Board.standard, sq("d8"), noHl)
    assertEquals(tc.getCharacter, '\u265B')

  test("renderSquare: empty square shows space"):
    val tc = BoardComponent.renderSquare(Board.empty, sq("e4"), noHl)
    assertEquals(tc.getCharacter, ' ')

  // ── capturedPieces ────────────────────────────────────────────────────────────

  test("capturedPieces: normal capture returns the captured opponent piece"):
    val blackPawn = Piece(Color.Black, PieceType.Pawn)
    val before    = Board(Map(sq("e5") -> blackPawn))
    val after     = Board(Map.empty)
    assertEquals(SidePanel.capturedPieces(before, after, Color.White), List(blackPawn))

  test("capturedPieces: moving player own piece not included"):
    val whitePawn = Piece(Color.White, PieceType.Pawn)
    val before    = Board(Map(sq("e2") -> whitePawn))
    val after     = Board(Map(sq("e4") -> whitePawn))
    assertEquals(SidePanel.capturedPieces(before, after, Color.White), List.empty)

  test("capturedPieces: promotion-capture returns captured piece, not moving pawn"):
    val whitePawn  = Piece(Color.White, PieceType.Pawn)
    val blackRook  = Piece(Color.Black, PieceType.Rook)
    val whiteQueen = Piece(Color.White, PieceType.Queen)
    val before = Board(Map(sq("e7") -> whitePawn, sq("d8") -> blackRook))
    val after  = Board(Map(sq("d8") -> whiteQueen))
    assertEquals(SidePanel.capturedPieces(before, after, Color.White), List(blackRook))

  test("capturedPieces: en passant capture returns the captured pawn"):
    val whitePawn = Piece(Color.White, PieceType.Pawn)
    val blackPawn = Piece(Color.Black, PieceType.Pawn)
    val before    = Board(Map(sq("e5") -> whitePawn, sq("d5") -> blackPawn))
    val after     = Board(Map(sq("d6") -> whitePawn))
    assertEquals(SidePanel.capturedPieces(before, after, Color.White), List(blackPawn))

  test("capturedPieces: promotion without capture returns empty"):
    val whitePawn  = Piece(Color.White, PieceType.Pawn)
    val whiteQueen = Piece(Color.White, PieceType.Queen)
    val before = Board(Map(sq("e7") -> whitePawn))
    val after  = Board(Map(sq("e8") -> whiteQueen))
    assertEquals(SidePanel.capturedPieces(before, after, Color.White), List.empty)

  // ── moveNotation ──────────────────────────────────────────────────────────────

  test("moveNotation: normal move"):
    assertEquals(SidePanel.moveNotation(NormalMove(sq("e2"), sq("e4"), None)), "e2-e4")

  test("moveNotation: promotion appends piece letter"):
    assertEquals(
      SidePanel.moveNotation(NormalMove(sq("e7"), sq("e8"), Some(PieceType.Queen))),
      "e7-e8=Q"
    )

  test("moveNotation: kingside castling"):
    // rookFrom(h1) file=7 > from(e1) file=4
    assertEquals(
      SidePanel.moveNotation(CastlingMove(sq("e1"), sq("g1"), sq("h1"), sq("f1"))),
      "O-O"
    )

  test("moveNotation: queenside castling"):
    // rookFrom(a1) file=0 < from(e1) file=4
    assertEquals(
      SidePanel.moveNotation(CastlingMove(sq("e1"), sq("c1"), sq("a1"), sq("d1"))),
      "O-O-O"
    )

  test("moveNotation: en passant uses same file-rank format"):
    assertEquals(
      SidePanel.moveNotation(EnPassantMove(sq("e5"), sq("d6"), sq("d5"))),
      "e5-d6"
    )

  // ── pieceSymbol ───────────────────────────────────────────────────────────────

  test("pieceSymbol: white king returns ♔"):
    assertEquals(SidePanel.pieceSymbol(Piece(Color.White, PieceType.King)), "\u2654")

  test("pieceSymbol: black pawn returns ♟"):
    assertEquals(SidePanel.pieceSymbol(Piece(Color.Black, PieceType.Pawn)), "\u265F")
