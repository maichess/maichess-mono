package org.maichess.mono.uifx

import java.util.concurrent.atomic.{AtomicBoolean, AtomicReference}
import org.maichess.mono.model.Color

/** Counts down both players' clocks on a background daemon thread.
 *  All state mutations are lock-free via AtomicReference. */
class ChessClock(cfg: ClockConfig, onTick: ClockState => Unit):
  private val state    = new AtomicReference[ClockState](ClockState.fromConfig(cfg))
  private val isPaused = new AtomicBoolean(true)   // starts paused — clock activates on first move
  private val isAlive  = new AtomicBoolean(true)

  def snapshot: ClockState = state.get()

  /** Activates the clock for the first time, counting down for `active`. */
  def start(active: Color): Unit =
    state.updateAndGet(_.copy(running = true, activeFor = active))
    isPaused.set(false)
    ()

  /** Called after a move: credits increment to the player who just moved, then switches to `newActive`. */
  def press(newActive: Color): Unit =
    state.updateAndGet { s =>
      val credited = s.activeFor match
        case Color.White => s.copy(whiteMs = s.whiteMs + cfg.incrementMs)
        case Color.Black => s.copy(blackMs = s.blackMs + cfg.incrementMs)
      credited.copy(activeFor = newActive)
    }
    ()

  /** Moves the active-player pointer without changing any times (used after undo/redo). */
  def updateActive(color: Color): Unit =
    state.updateAndGet(_.copy(activeFor = color))
    ()

  /** Stops the running flag without resetting times (used when undoing back to the start). */
  def deactivate(): Unit =
    state.updateAndGet(_.copy(running = false))
    isPaused.set(true)
    ()

  def pause(): Unit  = isPaused.set(true)
  def resume(): Unit = isPaused.set(false)

  /** Permanently stops the tick thread; call this when the game is discarded. */
  def stop(): Unit = isAlive.set(false)

  private val thread = new Thread(() => tickLoop())
  thread.setDaemon(true)
  thread.start()

  private def tickLoop(): Unit =
    var lastTick = System.currentTimeMillis()
    while isAlive.get() do
      Thread.sleep(100L)
      val now     = System.currentTimeMillis()
      val elapsed = now - lastTick
      lastTick    = now
      if !isPaused.get() then applyTick(elapsed)

  private def applyTick(elapsed: Long): Unit =
    val s = state.get()
    if s.running then
      val updated = state.updateAndGet(decremented(_, elapsed))
      onTick(updated)
      if updated.flagged.isDefined then isAlive.set(false)

  private def decremented(s: ClockState, elapsed: Long): ClockState =
    s.activeFor match
      case Color.White => s.copy(whiteMs = math.max(0L, s.whiteMs - elapsed))
      case Color.Black => s.copy(blackMs = math.max(0L, s.blackMs - elapsed))
