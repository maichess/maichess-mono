package chess.rules

/** Encodes which castling options remain available for both players. */
case class CastlingRights(
  whiteKingSide: Boolean,
  whiteQueenSide: Boolean,
  blackKingSide: Boolean,
  blackQueenSide: Boolean,
)

object CastlingRights:
  /** All four castling rights available — the standard starting state. */
  val all: CastlingRights = CastlingRights(true, true, true, true)

  /** No castling rights — used when constructing isolated test positions. */
  val none: CastlingRights = CastlingRights(false, false, false, false)
