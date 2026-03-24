package chess

import chess.model.*
import munit.FunSuite

class ModelSuite extends FunSuite:

  // Square.fromAlgebraic ─────────────────────────────────────────────────────

  test("Square.fromAlgebraic returns correct file for e4"):
    val sq = Square.fromAlgebraic("e4")
    assertEquals(sq.map(_.file.toChar), Some('e'))

  test("Square.fromAlgebraic returns correct rank for e4"):
    val sq = Square.fromAlgebraic("e4")
    assertEquals(sq.map(_.rank.toInt), Some(4))

  test("Square.fromAlgebraic round-trips all 64 squares"):
    val all = Square.all
    assertEquals(all.size, 64)
    all.foreach(sq => assertEquals(Square.fromAlgebraic(sq.toAlgebraic), Some(sq)))

  test("Square.fromAlgebraic returns None for out-of-range file"):
    assertEquals(Square.fromAlgebraic("z4"), None)

  test("Square.fromAlgebraic returns None for out-of-range rank"):
    assertEquals(Square.fromAlgebraic("e9"), None)

  test("Square.fromAlgebraic returns None for empty string"):
    assertEquals(Square.fromAlgebraic(""), None)

  test("Square.fromAlgebraic returns None for too-short string"):
    assertEquals(Square.fromAlgebraic("e"), None)

  test("Square.fromAlgebraic returns None for too-long string"):
    assertEquals(Square.fromAlgebraic("e44"), None)

  // Color.opposite ───────────────────────────────────────────────────────────

  test("Color.White.opposite is Black"):
    assertEquals(Color.White.opposite, Color.Black)

  test("Color.Black.opposite is White"):
    assertEquals(Color.Black.opposite, Color.White)

  test("Color.opposite is its own inverse"):
    assertEquals(Color.White.opposite.opposite, Color.White)
    assertEquals(Color.Black.opposite.opposite, Color.Black)

  // File / Rank ──────────────────────────────────────────────────────────────

  test("File.fromChar accepts a–h"):
    ('a' to 'h').foreach(c => assert(File.fromChar(c).isDefined, s"$c should parse"))

  test("File.fromChar rejects characters outside a–h"):
    List('A', 'i', '1', ' ').foreach(c => assertEquals(File.fromChar(c), None))

  test("Rank.fromInt accepts 1–8"):
    (1 to 8).foreach(i => assert(Rank.fromInt(i).isDefined, s"$i should parse"))

  test("Rank.fromInt rejects values outside 1–8"):
    List(0, 9, -1).foreach(i => assertEquals(Rank.fromInt(i), None))
