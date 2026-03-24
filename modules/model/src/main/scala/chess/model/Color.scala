package chess.model

/** Represents the color of a chess piece or player. */
enum Color:
  case White, Black

  /** Returns the opposing color. */
  def opposite: Color = this match
    case White => Black
    case Black => White
