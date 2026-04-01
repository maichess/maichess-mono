package org.maichess.mono.bots.engine

import org.maichess.mono.bots.Bot
import org.maichess.mono.engine.{Fen, GameState}
import org.maichess.mono.model
import org.maichess.mono.rules.Situation

// UCI-strength bot backed by iterative-deepening alpha-beta search.
// Bridges GameState → Position via FEN, delegates to Search.bestMove,
// then maps the engine move back to the rules-layer Move hierarchy.
@SuppressWarnings(Array("org.wartremover.warts.OptionPartial"))
class UciBot extends Bot:
  val name: String = "UCI Engine"

  def chooseMove(state: GameState): Option[model.Move] =
    val pos = Position.fromFen(Fen.encode(state.current))
    val em  = Search.bestMove(pos, timeLimitMs = 1000L)
    if em == Move.None then None else Some(toRulesMove(em, state.current))

  private def toRulesMove(em: Int, sit: Situation): model.Move =
    val from = r(Move.from(em).toInt); val to = r(Move.to(em).toInt)
    val fl   = Move.flag(em)
    if fl == Move.FlagKCastle then
      val (rf, rt) = if sit.turn == model.Color.White then (r(7), r(5)) else (r(63), r(61))
      model.CastlingMove(from, to, rf, rt)
    else if fl == Move.FlagQCastle then
      val (rf, rt) = if sit.turn == model.Color.White then (r(0), r(3)) else (r(56), r(59))
      model.CastlingMove(from, to, rf, rt)
    else if fl == Move.FlagEP then
      val capSq = if sit.turn == model.Color.White then Move.to(em).toInt - 8 else Move.to(em).toInt + 8
      model.EnPassantMove(from, to, r(capSq))
    else if (fl & 8) != 0 then
      model.NormalMove(from, to, Some(pt(Move.promoType(em))))
    else
      model.NormalMove(from, to, None)

  private def r(sq: Int): model.Square =
    model.Square(model.File.fromInt(sq & 7).get, model.Rank.fromInt(sq >> 3).get)

  private def pt(ptype: Int): model.PieceType = ptype match
    case PType.Knight => model.PieceType.Knight
    case PType.Bishop => model.PieceType.Bishop
    case PType.Rook   => model.PieceType.Rook
    case _            => model.PieceType.Queen
