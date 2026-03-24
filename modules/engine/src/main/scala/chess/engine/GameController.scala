package chess.engine

import chess.model.Move
import chess.rules.{RuleSet, Situation}

/**
 * Orchestrates a chess game: accepts moves, enforces legality, detects end conditions.
 *
 * @param ruleSet The rule set that governs move legality and game-ending conditions.
 */
class GameController(ruleSet: RuleSet):

  /** Creates a new game in the standard starting position. */
  def newGame(): GameState =
    GameState(history = Nil, current = Situation.standard)

  /**
   * Attempts to apply `move` to `state`.
   *
   * @return [[Right]] with the new [[GameState]] if the move is legal,
   *         or [[Left]] with an [[IllegalMove]] description if it is not.
   */
  def applyMove(state: GameState, move: Move): Either[IllegalMove, GameState] =
    val legal = ruleSet.allLegalMoves(state.current)
    if !legal.contains(move) then Left(IllegalMove(move, "Not a legal move in this position"))
    else
      val next = ruleSet.applyMove(state.current, move)
      Right(GameState(history = state.current :: state.history, current = next))

  /**
   * Returns the [[GameResult]] if the game has ended, or [[None]] if play continues.
   * Checks checkmate, stalemate, fifty-move rule, insufficient material, and threefold repetition.
   */
  def gameResult(state: GameState): Option[GameResult] =
    val sit = state.current
    if ruleSet.isCheckmate(sit) then
      Some(GameResult.Checkmate(sit.turn.opposite))
    else if ruleSet.isStalemate(sit) then
      Some(GameResult.Stalemate)
    else if ruleSet.isFiftyMoveRule(sit) then
      Some(GameResult.Draw(DrawReason.FiftyMoveRule))
    else if ruleSet.isInsufficientMaterial(sit) then
      Some(GameResult.Draw(DrawReason.InsufficientMaterial))
    else if isThreefoldRepetition(state) then
      Some(GameResult.Draw(DrawReason.ThreefoldRepetition))
    else None

  private def isThreefoldRepetition(state: GameState): Boolean =
    val sit       = state.current
    val allStates = sit :: state.history
    val matching  = allStates.count(s =>
      s.board            == sit.board &&
      s.turn             == sit.turn &&
      s.castlingRights   == sit.castlingRights &&
      s.enPassantSquare  == sit.enPassantSquare
    )
    matching >= 3
