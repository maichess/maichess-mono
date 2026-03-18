package org.maichess.mono.model

enum Color:
  case White, Black

  /** Returns the opposing color. */
  def opposite: Color = this match
    case White => Black
    case Black => White
