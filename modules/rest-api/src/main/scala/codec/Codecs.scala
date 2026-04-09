package org.maichess.mono.api.codec

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import org.maichess.mono.api.model.*

object Codecs:

  // Request decoders (order matters for nested types)
  given Decoder[ClockConfigRequest] = deriveDecoder
  given Decoder[NewGameRequest]     = deriveDecoder
  given Decoder[MoveRequest]        = deriveDecoder
  given Decoder[FenRequest]         = deriveDecoder
  given Decoder[PgnRequest]         = deriveDecoder

  // Response encoders (inner types first)
  given Encoder[PieceResponse]       = deriveEncoder
  given Encoder[MoveTargetResponse]  = deriveEncoder
  given Encoder[LegalMovesResponse]  = deriveEncoder
  given Encoder[SquareMovesResponse] = deriveEncoder
  given Encoder[ClockResponse]       = deriveEncoder
  given Encoder[GameResultResponse]  = deriveEncoder
  given Encoder[MetadataResponse]    = deriveEncoder
  given Encoder[GameStateResponse]   = deriveEncoder
  given Encoder[BotResponse]         = deriveEncoder
  given Encoder[FenResponse]         = deriveEncoder
  given Encoder[PgnResponse]         = deriveEncoder
  given Encoder[PauseResponse]       = deriveEncoder
