package org.maichess.mono.uifx

import org.maichess.mono.engine.*
import org.maichess.mono.model.*
import org.maichess.mono.rules.StandardRules

enum Change:
  case Move, Undo, Redo, Reset

class SharedGameModel(ctrl: GameController):
  private var current: SharedGameModel.State =
    SharedGameModel.State(ctrl.newGame(), Nil, Nil, Change.Reset)
  private var observers:         List[SharedGameModel.State => Unit] = Nil
  private var shutdownObservers: List[() => Unit]                   = Nil

  def state: SharedGameModel.State = synchronized { current }

  def addObserver(f: SharedGameModel.State => Unit): Unit = synchronized:
    observers = f :: observers

  def addShutdownObserver(f: () => Unit): Unit = synchronized:
    shutdownObservers = f :: shutdownObservers

  def shutdown(): Unit =
    val obs = synchronized { shutdownObservers }
    obs.foreach(_())

  private def commit(s: SharedGameModel.State): Unit =
    val obs = synchronized:
      current = s
      observers
    obs.foreach(_(s))

  def newGame(): Unit =
    commit(SharedGameModel.State(ctrl.newGame(), Nil, Nil, Change.Reset))

  def applyMove(move: Move, notation: String): Either[IllegalMove, Unit] =
    val (moveResult, toNotify) = synchronized:
      ctrl.applyMove(current.game, move) match
        case Right(g) =>
          val ns = current.copy(
            game              = g,
            moveHistory       = current.moveHistory :+ notation,
            futureMoveHistory = Nil,
            change            = Change.Move
          )
          current = ns
          (Right(()), Some((ns, observers)))
        case Left(e) =>
          (Left(e), None)
    toNotify.foreach { (s, obs) => obs.foreach(_(s)) }
    moveResult

  def undo(): Unit =
    val toNotify = synchronized:
      ctrl.undo(current.game).map { g =>
        val ns = current.copy(
          game              = g,
          futureMoveHistory = current.moveHistory.lastOption.toList ++ current.futureMoveHistory,
          moveHistory       = current.moveHistory.dropRight(1),
          change            = Change.Undo
        )
        current = ns
        (ns, observers)
      }
    toNotify.foreach { (s, obs) => obs.foreach(_(s)) }

  def redo(): Unit =
    val toNotify = synchronized:
      ctrl.redo(current.game).map { g =>
        val base = current.futureMoveHistory match
          case head :: rest =>
            current.copy(game = g, moveHistory = current.moveHistory :+ head, futureMoveHistory = rest)
          case Nil =>
            current.copy(game = g)
        val ns = base.copy(change = Change.Redo)
        current = ns
        (ns, observers)
      }
    toNotify.foreach { (s, obs) => obs.foreach(_(s)) }

  def importFen(fen: String): Either[String, Unit] =
    Fen.decode(fen) match
      case Right(sit) =>
        commit(SharedGameModel.State(GameState(Nil, sit), Nil, Nil, Change.Reset))
        Right(())
      case Left(err) => Left(err)

  def importPgn(pgn: String): Either[String, Unit] =
    Pgn.decode(pgn.trim, StandardRules) match
      case Right(g) =>
        commit(SharedGameModel.State(g, reconstructMoveHistory(g), Nil, Change.Reset))
        Right(())
      case Left(err) => Left(err)

  private def reconstructMoveHistory(game: GameState): List[String] =
    val situations = (game.current :: game.history).reverse
    situations.zip(situations.drop(1)).flatMap { case (before, after) =>
      StandardRules.allLegalMoves(before)
        .find(m => before.board.applyMove(m) == after.board)
        .map(moveNotation)
    }

  private def moveNotation(move: Move): String = move match
    case NormalMove(from, to, None)         => from.toAlgebraic + "-" + to.toAlgebraic
    case NormalMove(from, to, Some(pt))     => from.toAlgebraic + "-" + to.toAlgebraic + "=" + promoLetter(pt)
    case CastlingMove(from, _, rookFrom, _) =>
      if rookFrom.file.toInt > from.file.toInt then "O-O" else "O-O-O"
    case EnPassantMove(from, to, _)         => from.toAlgebraic + "-" + to.toAlgebraic

  private def promoLetter(pt: PieceType): String = pt match
    case PieceType.Queen  => "Q"
    case PieceType.Rook   => "R"
    case PieceType.Bishop => "B"
    case PieceType.Knight => "N"
    case PieceType.King   => "K"
    case PieceType.Pawn   => "P"

  def exportFen(): String    = Fen.encode(state.game.current)
  def exportPgn(): String    = Pgn.encode(state.game, StandardRules)
  def gameResult(): Option[GameResult] = ctrl.gameResult(state.game)

object SharedGameModel:
  case class State(
    game:              GameState,
    moveHistory:       List[String],
    futureMoveHistory: List[String],
    change:            Change
  )
