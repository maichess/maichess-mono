package org.maichess.mono.ui

import org.maichess.mono.model.*

enum Direction:
  case Up, Down, Left, Right

enum CursorState:
  case Navigating(cursor: Square)
  case PieceSelected(from: Square, index: Int, targets: IndexedSeq[Move])

object UIState:

  def cursorSquare(cs: CursorState): Square = cs match
    case CursorState.Navigating(cursor)               => cursor
    case CursorState.PieceSelected(_, index, targets) => targets(index).to

  def selectedSquare(cs: CursorState): Option[Square] = cs match
    case CursorState.Navigating(_)             => None
    case CursorState.PieceSelected(from, _, _) => Some(from)

  def targetSquares(cs: CursorState): Set[Square] = cs match
    case CursorState.Navigating(_)                => Set.empty
    case CursorState.PieceSelected(_, _, targets) => targets.map(_.to).toSet

  def moveCursorFree(cursor: Square, dir: Direction): Square =
    val df = dir match
      case Direction.Left  => -1
      case Direction.Right =>  1
      case _               =>  0
    val dr = dir match
      case Direction.Up   =>  1
      case Direction.Down => -1
      case _              =>  0
    val newFile = (cursor.file.toInt + df + 8) % 8
    val newRank = (cursor.rank.toInt + dr + 8) % 8
    (for f <- File.fromInt(newFile); r <- Rank.fromInt(newRank) yield Square(f, r))
      .getOrElse(cursor)

  def moveCursorTargets(targets: IndexedSeq[Move], index: Int, dir: Direction): Int =
    val delta = dir match
      case Direction.Right | Direction.Down => 1
      case Direction.Left  | Direction.Up   => -1
    (index + delta + targets.length) % targets.length
