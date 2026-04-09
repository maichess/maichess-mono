package org.maichess.mono.api.model

case class ClockConfigRequest(initialMs: Long, incrementMs: Long)

case class NewGameRequest(
  whiteName:   Option[String],
  blackName:   Option[String],
  whiteBot:    Option[String],
  blackBot:    Option[String],
  clockConfig: Option[ClockConfigRequest]
)

case class MoveRequest(from: String, to: String, promotion: Option[String])

case class FenRequest(fen: String)

case class PgnRequest(pgn: String)
