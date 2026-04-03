package org.maichess.mono.tests

import munit.FunSuite
import org.maichess.mono.engine.{GameController, PgnMetadata}
import org.maichess.mono.rules.StandardRules
import org.maichess.mono.uifx.{ClockConfig, SharedGameModel}

class SharedGameModelSuite extends FunSuite:

  def makeModel(): SharedGameModel = new SharedGameModel(new GameController(StandardRules))

  test("importPgn empty game produces empty moveHistory"):
    val model = makeModel()
    model.importPgn("*") match
      case Right(()) => assertEquals(model.state.moveHistory, Nil)
      case Left(err) => fail(err)

  test("importPgn single move populates moveHistory"):
    val model = makeModel()
    model.importPgn("1. e4 *") match
      case Right(()) => assertEquals(model.state.moveHistory, List("e4"))
      case Left(err) => fail(err)

  test("importPgn multiple moves populates full moveHistory"):
    val model = makeModel()
    model.importPgn("1. e4 e5 2. Nf3 *") match
      case Right(()) => assertEquals(model.state.moveHistory, List("e4", "e5", "Nf3"))
      case Left(err) => fail(err)

  test("importPgn invalid notation returns Left"):
    val model = makeModel()
    assert(model.importPgn("1. Xz5 *").isLeft)

  test("importPgn clears futureMoveHistory"):
    val model = makeModel()
    model.importPgn("1. e4 *") match
      case Right(()) => assertEquals(model.state.futureMoveHistory, Nil)
      case Left(err) => fail(err)

  test("undo after importPgn removes last move from history"):
    val model = makeModel()
    model.importPgn("1. e4 e5 *") match
      case Right(()) =>
        model.undo()
        assertEquals(model.state.moveHistory, List("e4"))
        assertEquals(model.state.futureMoveHistory, List("e5"))
      case Left(err) => fail(err)

  test("undo down to empty history after importPgn"):
    val model = makeModel()
    model.importPgn("1. e4 *") match
      case Right(()) =>
        model.undo()
        assertEquals(model.state.moveHistory, Nil)
        assertEquals(model.state.futureMoveHistory, List("e4"))
      case Left(err) => fail(err)

  test("redo after undo after importPgn restores moveHistory"):
    val model = makeModel()
    model.importPgn("1. e4 e5 *") match
      case Right(()) =>
        model.undo()
        model.redo()
        assertEquals(model.state.moveHistory, List("e4", "e5"))
        assertEquals(model.state.futureMoveHistory, Nil)
      case Left(err) => fail(err)

  test("importPgn with kingside castling shows O-O notation"):
    val pgn = "1. e4 e5 2. Nf3 Nc6 3. Bc4 Bc5 4. O-O *"
    val model = makeModel()
    model.importPgn(pgn) match
      case Right(()) => assert(model.state.moveHistory.contains("O-O"))
      case Left(err) => fail(err)

  test("importPgn with en passant shows SAN notation"):
    val pgn = "1. e4 d5 2. e5 f5 3. exf6 *"
    val model = makeModel()
    model.importPgn(pgn) match
      case Right(()) => assert(model.state.moveHistory.contains("exf6"))
      case Left(err) => fail(err)

  test("importFen clears moveHistory"):
    val model = makeModel()
    model.importPgn("1. e4 *") match
      case Right(()) =>
        model.importFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") match
          case Right(()) => assertEquals(model.state.moveHistory, Nil)
          case Left(err) => fail(err)
      case Left(err) => fail(err)

  test("newGame clears moveHistory"):
    val model = makeModel()
    model.importPgn("1. e4 *") match
      case Right(()) =>
        model.newGame()
        assertEquals(model.state.moveHistory, Nil)
      case Left(err) => fail(err)

  test("newGame with names sets metadata white and black"):
    val model = makeModel()
    model.newGame(whiteName = "Alice", blackName = "Bob")
    assertEquals(model.state.metadata.white, "Alice")
    assertEquals(model.state.metadata.black, "Bob")

  test("newGame with names sets metadata event"):
    val model = makeModel()
    model.newGame(whiteName = "Alice", blackName = "Bob")
    assertEquals(model.state.metadata.event, "Alice vs Bob")

  test("newGame default names produce default metadata"):
    val model = makeModel()
    model.newGame()
    assertEquals(model.state.metadata.white, "White")
    assertEquals(model.state.metadata.black, "Black")

  test("importPgn preserves header metadata"):
    val model = makeModel()
    val pgn =
      """[Event "My Event"]
        |[White "Alice"]
        |[Black "Bob"]
        |
        |1. e4 *""".stripMargin
    model.importPgn(pgn) match
      case Right(()) =>
        assertEquals(model.state.metadata.white, "Alice")
        assertEquals(model.state.metadata.black, "Bob")
        assertEquals(model.state.metadata.event, "My Event")
      case Left(err) => fail(err)

  test("newGame without clockConfig leaves clock as None"):
    val model = makeModel()
    model.newGame()
    assertEquals(model.state.clock, None)

  test("newGame with clockConfig creates clock state"):
    val model = makeModel()
    model.newGame(clockConfig = Some(ClockConfig(60_000L, 0L)))
    assert(model.state.clock.isDefined)

  test("importFen resets clock to None"):
    val model = makeModel()
    model.newGame(clockConfig = Some(ClockConfig(60_000L, 0L)))
    model.importFen("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1") match
      case Right(()) => assertEquals(model.state.clock, None)
      case Left(err) => fail(err)
