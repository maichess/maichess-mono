package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.{RuleSet, Situation}

object Pgn:

  /** Encodes the full game history as a PGN string. */
  def encode(state: GameState, ruleSet: RuleSet): String =
    val ctrl   = new GameController(ruleSet)
    val result = resultTag(state, ctrl)
    val header = List(
      "[Event \"?\"]",
      "[Site \"?\"]",
      "[Date \"????.??.??\"]",
      "[Round \"?\"]",
      "[White \"?\"]",
      "[Black \"?\"]",
      "[Result \"" + result + "\"]"
    ).mkString("\n")
    val moves = encodeMoves(state, ruleSet)
    header + "\n\n" + moves + " " + result

  /** Imports a PGN string; returns the resulting GameState or an error. */
  def decode(pgn: String, ruleSet: RuleSet): Either[String, GameState] =
    val tokens  = tokenize(stripHeaders(pgn))
    val ctrl    = new GameController(ruleSet)
    val initial = GameState(Nil, Situation.standard)
    parseTokens(tokens, ruleSet).map(ctrl.replay(_).finalState(initial))

  private def parseTokens(tokens: List[String], ruleSet: RuleSet): Either[String, List[Move]] =
    tokens.foldLeft(Right((Situation.standard, List.empty[Move])): Either[String, (Situation, List[Move])]) {
      case (Right((sit, moves)), token) =>
        San.decode(sit, token, ruleSet) match
          case Right(move) => Right((sit.advance(move), moves :+ move))
          case Left(err)   => Left("Cannot parse SAN '" + token + "': " + err)
      case (left, _) => left
    }.map(_._2)

  private def resultTag(state: GameState, ctrl: GameController): String =
    ctrl.gameResult(state) match
      case Some(GameResult.Checkmate(Color.White)) => "1-0"
      case Some(GameResult.Checkmate(Color.Black)) => "0-1"
      case Some(_)                                  => "1/2-1/2"
      case None                                     => "*"

  private def encodeMoves(state: GameState, ruleSet: RuleSet): String =
    val situations = (state.current :: state.history).reverse
    val tokens = situations.zip(situations.drop(1)).flatMap { case (before, after) =>
      val move   = findMove(before, after, ruleSet)
      val sanStr = move.fold("?")(m => San.encode(before, m, after, ruleSet))
      val num    = if before.turn == Color.White then List(before.fullMoveNumber.toString + ".") else Nil
      num :+ sanStr
    }
    tokens.mkString(" ")

  private def findMove(before: Situation, after: Situation, ruleSet: RuleSet): Option[Move] =
    ruleSet.allLegalMoves(before).find(m => before.board.applyMove(m) == after.board)

  private def stripHeaders(pgn: String): String =
    pgn.linesIterator
      .dropWhile(_.trim.startsWith("["))
      .mkString(" ")
      .trim

  private def tokenize(body: String): List[String] =
    val noComments = body.replaceAll("\\{[^}]*\\}", "").replaceAll("\\$\\d+", "").replaceAll("[!?]+", "")
    val spaced     = noComments.replaceAll("(\\d+\\.+)", " $1 ")
    spaced.split("\\s+").toList.filter(t =>
      t.nonEmpty &&
      !t.matches("\\d+\\.+") &&
      !Set("*", "1-0", "0-1", "1/2-1/2").contains(t)
    )
