package org.maichess.mono.engine

import org.maichess.mono.model.*
import org.maichess.mono.rules.{RuleSet, Situation}

object Pgn:

  /** Encodes the full game history as a PGN string using the supplied metadata for the seven-tag roster. */
  def encode(state: GameState, ruleSet: RuleSet, metadata: PgnMetadata): String =
    val ctrl   = new GameController(ruleSet)
    val result = resultTag(state, ctrl)
    val header = List(
      "[Event \""  + metadata.event + "\"]",
      "[Site \""   + metadata.site  + "\"]",
      "[Date \""   + metadata.date  + "\"]",
      "[Round \""  + metadata.round + "\"]",
      "[White \""  + metadata.white + "\"]",
      "[Black \""  + metadata.black + "\"]",
      "[Result \"" + result         + "\"]"
    ).mkString("\n")
    val moves = encodeMoves(state, ruleSet)
    header + "\n\n" + moves + " " + result

  /** Imports a PGN string; returns the resulting GameState and the parsed header metadata, or an error. */
  def decode(pgn: String, ruleSet: RuleSet): Either[String, (GameState, PgnMetadata)] =
    val (meta, body) = parseHeaders(pgn.trim)
    val tokens       = tokenize(body)
    val initial      = GameState(Nil, Situation.standard)
    val ctrl         = new GameController(ruleSet)
    tokens.foldLeft(Right(initial): Either[String, GameState]) {
      case (Right(s), token) =>
        San.decode(s.current, token, ruleSet) match
          case Right(move) => Right(ctrl.advance(s, move))
          case Left(err)   => Left("Cannot parse SAN '" + token + "': " + err)
      case (left, _) => left
    }.map(gs => (gs, meta))

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

  /** Splits PGN text into parsed metadata and the move body. */
  private def parseHeaders(pgn: String): (PgnMetadata, String) =
    val lines       = pgn.linesIterator.toList
    val headerLines = lines.takeWhile(_.trim.startsWith("["))
    val body        = lines.dropWhile(_.trim.startsWith("[")).mkString(" ").trim
    val tags        = headerLines.flatMap(extractTag).toMap
    val meta        = PgnMetadata(
      event = tags.getOrElse("Event", "?"),
      site  = tags.getOrElse("Site",  "?"),
      date  = tags.getOrElse("Date",  "????.??.??"),
      round = tags.getOrElse("Round", "1"),
      white = tags.getOrElse("White", "White"),
      black = tags.getOrElse("Black", "Black")
    )
    (meta, body)

  /** Extracts a (tagName, tagValue) pair from a single PGN header line, or None if malformed. */
  private def extractTag(line: String): Option[(String, String)] =
    """\[(\w+)\s+"([^"]*)"\]""".r
      .findFirstMatchIn(line.trim)
      .map(m => m.group(1) -> m.group(2))

  private def tokenize(body: String): List[String] =
    val noComments = body.replaceAll("\\{[^}]*\\}", "").replaceAll("\\$\\d+", "").replaceAll("[!?]+", "")
    val spaced     = noComments.replaceAll("(\\d+\\.+)", " $1 ")
    spaced.split("\\s+").toList.filter(t =>
      t.nonEmpty &&
      !t.matches("\\d+\\.+") &&
      !Set("*", "1-0", "0-1", "1/2-1/2").contains(t)
    )
