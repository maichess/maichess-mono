package org.maichess.mono.api.model

case class PieceResponse(color: String, `type`: String)

case class MoveTargetResponse(to: String, promotions: List[String])

case class LegalMovesResponse(moves: Map[String, List[MoveTargetResponse]])

case class SquareMovesResponse(square: String, moves: List[MoveTargetResponse])

case class ClockResponse(
  whiteMs:   Long,
  blackMs:   Long,
  running:   Boolean,
  activeFor: String,
  flagged:   Option[String]
)

case class GameResultResponse(`type`: String, winner: Option[String], reason: Option[String])

case class MetadataResponse(white: String, black: String)

case class GameStateResponse(
  board:             Map[String, PieceResponse],
  turn:              String,
  moveHistory:       List[String],
  futureMoveHistory: List[String],
  result:            Option[GameResultResponse],
  paused:            Boolean,
  resigned:          Boolean,
  clock:             Option[ClockResponse],
  metadata:          MetadataResponse
)

case class BotResponse(name: String)

case class FenResponse(fen: String)

case class PgnResponse(pgn: String)

case class PauseResponse(paused: Boolean)
