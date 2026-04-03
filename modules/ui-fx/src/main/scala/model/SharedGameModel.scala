package org.maichess.mono.uifx

import java.util.concurrent.atomic.{AtomicBoolean, AtomicInteger}
import org.maichess.mono.bots.Bot
import org.maichess.mono.engine.{Fen, GameController, GameResult, GameState, IllegalMove, Pgn, PgnMetadata, San}
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

enum Change:
  case Move, Undo, Redo, Reset, ClockTick

class SharedGameModel(ctrl: GameController):
  private var current: SharedGameModel.State =
    SharedGameModel.State(ctrl.newGame(), Nil, Nil, Change.Reset, PgnMetadata.default, None)
  private var observers:         List[SharedGameModel.State => Unit] = Nil
  private var shutdownObservers: List[() => Unit]                   = Nil
  private var whiteBot: Option[Bot]                                  = None
  private var blackBot: Option[Bot]                                  = None
  private var clock:    Option[ChessClock]                           = None
  private val isPaused:     AtomicBoolean = new AtomicBoolean(false)
  private val gameVersion:  AtomicInteger = new AtomicInteger(0)

  def state: SharedGameModel.State = synchronized { current }

  def addObserver(f: SharedGameModel.State => Unit): Unit = synchronized:
    observers = f :: observers

  def addShutdownObserver(f: () => Unit): Unit = synchronized:
    shutdownObservers = f :: shutdownObservers

  def shutdown(): Unit =
    val (obs, clk) = synchronized { (shutdownObservers, clock) }
    clk.foreach(_.stop())
    obs.foreach(_())

  private def commit(s: SharedGameModel.State): Unit =
    val obs = synchronized:
      current = s
      observers
    obs.foreach(_(s))

  def newGame(
    white:       Option[Bot]         = None,
    black:       Option[Bot]         = None,
    whiteName:   String              = "White",
    blackName:   String              = "Black",
    clockConfig: Option[ClockConfig] = None
  ): Unit =
    val oldClock = synchronized { val c = clock; clock = None; c }
    oldClock.foreach(_.stop())
    isPaused.set(false)
    gameVersion.incrementAndGet()
    val meta     = PgnMetadata.fromNames(whiteName, blackName)
    val newClock = clockConfig.map(cfg => new ChessClock(cfg, onTick))
    synchronized { whiteBot = white; blackBot = black; clock = newClock }
    commit(SharedGameModel.State(ctrl.newGame(), Nil, Nil, Change.Reset, meta, newClock.map(_.snapshot)))
    scheduleAiMoveIfNeeded()

  def applyMove(move: Move): Either[IllegalMove, Unit] =
    val (moveResult, toNotify) = synchronized:
      if current.clock.flatMap(_.flagged).isDefined then
        (Left(IllegalMove("Game over: time expired")), None)
      else
        ctrl.applyMove(current.game, move) match
          case Right(g) =>
            val isFirstMove = g.history.sizeIs == 1
            val newTurn     = g.current.turn
            val notation    = San.encode(current.game.current, move, g.current, StandardRules)
            val gameOver    = ctrl.gameResult(g).isDefined
            clock.foreach { c =>
              if isFirstMove then c.start(newTurn) else c.press(newTurn)
            }
            if gameOver then clock.foreach(_.deactivate())
            val ns = current.copy(
              game              = g,
              moveHistory       = current.moveHistory :+ notation,
              futureMoveHistory = Nil,
              change            = Change.Move,
              clock             = clock.map(_.snapshot)
            )
            current = ns
            (Right(()), Some((ns, observers)))
          case Left(e) =>
            (Left(e), None)
    toNotify.foreach { (s, obs) => obs.foreach(_(s)) }
    if moveResult.isRight then scheduleAiMoveIfNeeded()
    moveResult

  private def onTick(cs: ClockState): Unit =
    val (s, obs) = synchronized:
      val ns = current.copy(clock = Some(cs), change = Change.ClockTick)
      current = ns
      (ns, observers)
    obs.foreach(_(s))

  private def scheduleAiMoveIfNeeded(): Unit =
    val (wb, bb, st) = synchronized { (whiteBot, blackBot, current) }
    val isOver = ctrl.gameResult(st.game).isDefined || st.clock.flatMap(_.flagged).isDefined
    val botOpt = if st.game.current.turn == Color.White then wb else bb
    botOpt.foreach { b =>
      if !isOver then
        val thread = new Thread(() => runBotMove(b, st.game))
        thread.setDaemon(true)
        thread.start()
    }

  private def runBotMove(b: Bot, game: GameState): Unit =
    val v = gameVersion.get()
    b.chooseMove(game).foreach { move =>
      waitUntilUnpaused()
      val flagged = synchronized { current.clock.flatMap(_.flagged).isDefined }
      if !flagged && gameVersion.get() == v then
        val _ = applyMove(move)
    }

  private def waitUntilUnpaused(): Unit =
    while isPaused.get() do Thread.sleep(50L)

  def pause(): Unit =
    isPaused.set(true)
    synchronized { clock }.foreach(_.pause())

  def resume(): Unit =
    isPaused.set(false)
    synchronized { clock }.foreach(_.resume())
    scheduleAiMoveIfNeeded()

  def isPausedState: Boolean = isPaused.get()

  def undo(): Unit =
    val toNotify = synchronized:
      val hasClock = clock.isDefined
      if hasClock && !isPaused.get() then None
      else
        ctrl.undo(current.game).map { g =>
          gameVersion.incrementAndGet()
          clock.foreach { c =>
            if g.history.isEmpty then c.deactivate() else c.updateActive(g.current.turn)
          }
          val ns = current.copy(
            game              = g,
            futureMoveHistory = current.moveHistory.lastOption.toList ++ current.futureMoveHistory,
            moveHistory       = current.moveHistory.dropRight(1),
            change            = Change.Undo,
            clock             = clock.map(_.snapshot)
          )
          current = ns
          (ns, observers)
        }
    toNotify.foreach { (s, obs) => obs.foreach(_(s)) }

  def redo(): Unit =
    val toNotify = synchronized:
      val hasClock = clock.isDefined
      if hasClock && !isPaused.get() then None
      else
        ctrl.redo(current.game).map { g =>
          gameVersion.incrementAndGet()
          val wasAtStart = current.game.history.isEmpty
          val newTurn    = g.current.turn
          clock.foreach { c =>
            if wasAtStart && !isPaused.get() then c.start(newTurn)
            else if !wasAtStart then c.updateActive(newTurn)
          }
          val base = current.futureMoveHistory match
            case head :: rest =>
              current.copy(game = g, moveHistory = current.moveHistory :+ head, futureMoveHistory = rest)
            case Nil =>
              current.copy(game = g)
          val ns = base.copy(change = Change.Redo, clock = clock.map(_.snapshot))
          current = ns
          (ns, observers)
        }
    toNotify.foreach { (s, obs) => obs.foreach(_(s)) }

  def importFen(fen: String): Either[String, Unit] =
    Fen.decode(fen) match
      case Right(sit) =>
        val clk = synchronized { val c = clock; clock = None; c }
        clk.foreach(_.stop())
        isPaused.set(false)
        gameVersion.incrementAndGet()
        commit(SharedGameModel.State(GameState(Nil, sit), Nil, Nil, Change.Reset, PgnMetadata.default, None))
        Right(())
      case Left(err) => Left(err)

  def importPgn(pgn: String): Either[String, Unit] =
    Pgn.decode(pgn.trim, StandardRules) match
      case Right((g, meta)) =>
        val clk = synchronized { val c = clock; clock = None; c }
        clk.foreach(_.stop())
        isPaused.set(false)
        gameVersion.incrementAndGet()
        commit(SharedGameModel.State(g, reconstructMoveHistory(g), Nil, Change.Reset, meta, None))
        Right(())
      case Left(err) => Left(err)

  private def reconstructMoveHistory(game: GameState): List[String] =
    val situations = (game.current :: game.history).reverse
    situations.zip(situations.drop(1)).flatMap { case (before, after) =>
      StandardRules.allLegalMoves(before)
        .find(m => before.board.applyMove(m) == after.board)
        .map(m => San.encode(before, m, after, StandardRules))
    }

  def resign(): Unit =
    val clk = synchronized { val c = clock; clock = None; c }
    clk.foreach(_.stop())
    synchronized { whiteBot = None; blackBot = None }

  def botFor(color: Color): Option[Bot]  = synchronized { if color == Color.White then whiteBot else blackBot }
  def hasBot: Boolean                    = synchronized { whiteBot.isDefined || blackBot.isDefined }
  def exportFen(): String                = Fen.encode(state.game.current)
  def exportPgn(): String                = Pgn.encode(state.game, StandardRules, state.metadata)
  def gameResult(): Option[GameResult]   = ctrl.gameResult(state.game)

object SharedGameModel:
  case class State(
    game:              GameState,
    moveHistory:       List[String],
    futureMoveHistory: List[String],
    change:            Change,
    metadata:          PgnMetadata,
    clock:             Option[ClockState]
  )
