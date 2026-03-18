package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.model.*

class ModelSuite extends FunSuite:

  test("Square.fromAlgebraic e4 returns file e and rank 4"):
    val sq = Square.fromAlgebraic("e4")
    assertEquals(sq.isDefined, true)
    sq.foreach { s =>
      assertEquals(s.file.toChar, 'e')
      assertEquals(s.rank.toChar, '4')
    }

  test("Square.fromAlgebraic a1 round-trips to a1"):
    assertEquals(Square.fromAlgebraic("a1").map(_.toAlgebraic), Some("a1"))

  test("Square.fromAlgebraic empty string returns None"):
    assertEquals(Square.fromAlgebraic(""), None)

  test("Square.fromAlgebraic single char returns None"):
    assertEquals(Square.fromAlgebraic("e"), None)

  test("Square.fromAlgebraic invalid file returns None"):
    assertEquals(Square.fromAlgebraic("z4"), None)
    assertEquals(Square.fromAlgebraic("i1"), None)

  test("Square.fromAlgebraic invalid rank returns None"):
    assertEquals(Square.fromAlgebraic("e0"), None)
    assertEquals(Square.fromAlgebraic("e9"), None)

  test("Color.opposite White is Black"):
    assertEquals(Color.White.opposite, Color.Black)

  test("Color.opposite Black is White"):
    assertEquals(Color.Black.opposite, Color.White)

  test("Color.opposite is its own inverse"):
    assertEquals(Color.White.opposite.opposite, Color.White)

  test("Square.toAlgebraic round-trips for several squares"):
    List("a1", "h8", "e4", "d5", "a8", "h1").foreach { alg =>
      assertEquals(Square.fromAlgebraic(alg).map(_.toAlgebraic), Some(alg))
    }
