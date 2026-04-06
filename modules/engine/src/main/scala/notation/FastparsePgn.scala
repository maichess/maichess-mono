package org.maichess.mono.engine

import fastparse.*
import fastparse.MultiLineWhitespace.*
import org.maichess.mono.rules.{RuleSet, Situation}

/** PGN decoder backed by fastparse combinators. Encode delegates to [[Pgn]]. */
class FastparsePgn(san: SanCodec) extends PgnCodec:

  private val handwritten = new Pgn(san)

  def encode(state: GameState, ruleSet: RuleSet, metadata: PgnMetadata): String =
    handwritten.encode(state, ruleSet, metadata)

  def decode(pgn: String, ruleSet: RuleSet): Either[String, (GameState, PgnMetadata)] =
    parse(pgn.trim, pgnParser) match
      case Parsed.Success((meta, tokens), _) => applyMoves(tokens, meta, ruleSet)
      case f: Parsed.Failure                 => Left("Invalid PGN: " + f.msg)

  // ── Header grammar ─────────────────────────────────────────────────────────

  private def tagName(implicit ctx: P[Any]): P[String] =
    CharsWhile(c => c.isLetterOrDigit || c == '_').!

  private def tagValue(implicit ctx: P[Any]): P[String] =
    P("\"") ~ CharsWhile(_ != '"', 0).! ~ P("\"")

  private def header(implicit ctx: P[Any]): P[(String, String)] =
    P("[") ~ tagName ~ tagValue ~ P("]")

  private def headers(implicit ctx: P[Any]): P[Map[String, String]] =
    header.rep.map(_.toMap)

  // ── Move-text grammar ──────────────────────────────────────────────────────
  //
  // Each alternative returns Some(token) for SAN moves and None for noise
  // (move numbers, results, comments, annotations, NAGs).

  private def comment(implicit ctx: P[Any]): P[Option[String]] =
    P("{") ~ CharsWhile(_ != '}', 0) ~ P("}") ~ Pass.map(_ => None)

  private def nag(implicit ctx: P[Any]): P[Option[String]] =
    P("$") ~ CharsWhile(_.isDigit) ~ Pass.map(_ => None)

  private def annotation(implicit ctx: P[Any]): P[Option[String]] =
    CharIn("!?").rep(1) ~ Pass.map(_ => None)

  private def result(implicit ctx: P[Any]): P[Option[String]] =
    (P("1-0") | P("0-1") | P("1/2-1/2") | P("*")) ~ Pass.map(_ => None)

  private def moveNumber(implicit ctx: P[Any]): P[Option[String]] =
    CharsWhile(_.isDigit) ~ P(".").rep(1) ~ Pass.map(_ => None)

  private def moveToken(implicit ctx: P[Any]): P[Option[String]] =
    CharsWhile(c => !c.isWhitespace && c != '{' && c != '!' && c != '?').!.map(Some(_))

  private def moveTextElement(implicit ctx: P[Any]): P[Option[String]] =
    comment | nag | annotation | result | moveNumber | moveToken

  private def moveText(implicit ctx: P[Any]): P[List[String]] =
    moveTextElement.rep.map(_.collect { case Some(t) => t }.toList)

  // ── Top-level ──────────────────────────────────────────────────────────────

  private def pgnParser(p: P[Any]): P[(PgnMetadata, List[String])] =
    given P[Any] = p
    (headers ~ moveText ~ End).map { case (tags, tokens) => (metaFromTags(tags), tokens) }

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
