package org.maichess.mono.engine

import scala.util.parsing.combinator.RegexParsers
import org.maichess.mono.rules.{RuleSet, Situation}

/** PGN decoder backed by scala-parser-combinators. Encode delegates to [[Pgn]]. */
class PcPgn(san: SanCodec) extends PgnCodec with RegexParsers:

  // Default whitespace skipping is enabled for move-text; we opt in selectively.
  override val skipWhitespace = true

  private val handwritten = new Pgn(san)

  def encode(state: GameState, ruleSet: RuleSet, metadata: PgnMetadata): String =
    handwritten.encode(state, ruleSet, metadata)

  def decode(pgn: String, ruleSet: RuleSet): Either[String, (GameState, PgnMetadata)] =
    parseAll(pgnParser, pgn.trim) match
      case Success((meta, tokens), _) => applyMoves(tokens, meta, ruleSet)
      case err: NoSuccess @unchecked  => Left("Invalid PGN: " + err.msg)

  // ── Header grammar ─────────────────────────────────────────────────────────

  private def tagName: Parser[String]  = """[A-Za-z0-9_]+""".r
  private def tagValue: Parser[String] = "\"" ~> """[^"]*""".r <~ "\""

  private def header: Parser[(String, String)] =
    "[" ~> (tagName ~ tagValue) <~ "]" ^^ { case k ~ v => (k, v) }

  private def headers: Parser[Map[String, String]] =
    rep(header) ^^ (_.toMap)

  // ── Move-text grammar ──────────────────────────────────────────────────────
  //
  // Each alternative returns Some(token) for SAN moves and None for noise.

  private def comment: Parser[Option[String]] =
    "{" ~> """[^}]*""".r <~ "}" ^^^ None

  private def nag: Parser[Option[String]] =
    """\$\d+""".r ^^^ None

  private def annotation: Parser[Option[String]] =
    """[!?]+""".r ^^^ None

  private def result: Parser[Option[String]] =
    ("1-0" | "0-1" | "1/2-1/2" | "*") ^^^ None

  private def moveNumber: Parser[Option[String]] =
    """\d+\.+""".r ^^^ None

  private def moveToken: Parser[Option[String]] =
    """[^\s{!?]+""".r ^^ (Some(_))

  private def moveTextElement: Parser[Option[String]] =
    comment | nag | annotation | result | moveNumber | moveToken

  private def moveText: Parser[List[String]] =
    rep(moveTextElement) ^^ (_.collect { case Some(t) => t })

  // ── Top-level ──────────────────────────────────────────────────────────────

  private def pgnParser: Parser[(PgnMetadata, List[String])] =
    headers ~ moveText ^^ { case tags ~ tokens => (metaFromTags(tags), tokens) }

  private def metaFromTags(tags: Map[String, String]): PgnMetadata =
    PgnMetadata(
      event = tags.getOrElse("Event", "?"),
      site  = tags.getOrElse("Site",  "?"),
      date  = tags.getOrElse("Date",  "????.??.??"),
      round = tags.getOrElse("Round", "1"),
      white = tags.getOrElse("White", "White"),
      black = tags.getOrElse("Black", "Black")
    )

  // ── Move application ───────────────────────────────────────────────────────

  private def applyMoves(
    tokens:  List[String],
    meta:    PgnMetadata,
    ruleSet: RuleSet
  ): Either[String, (GameState, PgnMetadata)] =
    val initial = GameState(Nil, Situation.standard)
    val ctrl    = new GameController(ruleSet)
    tokens.foldLeft(Right(initial): Either[String, GameState]) {
      case (Right(state), token) =>
        san.decode(state.current, token, ruleSet) match
          case Right(move) => Right(ctrl.advance(state, move))
          case Left(err)   => Left("Cannot parse SAN '" + token + "': " + err)
      case (left, _) => left
    }.map(gs => (gs, meta))
