package org.maichess.mono.uifx

import org.maichess.mono.model.Color

/** Time budget for a single game, in milliseconds. */
case class ClockConfig(initialMs: Long, incrementMs: Long)

/** Immutable snapshot of both players' remaining time. Carried inside SharedGameModel.State. */
case class ClockState(
  whiteMs:   Long,
  blackMs:   Long,
  running:   Boolean,
  activeFor: Color
):
  def remainingFor(color: Color): Long =
    if color == Color.White then whiteMs else blackMs

  def isLow(color: Color): Boolean =
    val r = remainingFor(color)
    r > 0L && r < 30_000L

  /** Returns the color whose time reached zero, if any. */
  def flagged: Option[Color] =
    if whiteMs <= 0L then Some(Color.White)
    else if blackMs <= 0L then Some(Color.Black)
    else None

  /** Human-readable remaining time for one color.
   *  Shows tenths of a second for the final 10 seconds. */
  def formatted(color: Color): String =
    val ms = remainingFor(color)
    if ms <= 0L then "0:00"
    else if ms < 10_000L then
      val secs   = ms / 1000L
      val tenths = (ms % 1000L) / 100L
      secs.toString + "." + tenths.toString
    else
      val total = ms / 1000L
      val mins  = total / 60L
      val secs  = total % 60L
      mins.toString + ":" + f"$secs%02d"

object ClockState:
  def fromConfig(cfg: ClockConfig): ClockState =
    ClockState(
      whiteMs   = cfg.initialMs,
      blackMs   = cfg.initialMs,
      running   = false,
      activeFor = Color.White
    )
