package org.maichess.mono.engine

/** A chess game computation threading [[GameState]] through sequential half-moves.
 *
 * Concrete StateT[Either[[[GameResult]], *], [[GameState]], A]: `pure` lifts a value without
 * touching state; `flatMap` sequences operations and short-circuits the moment the game ends.
 *
 * Monad laws hold:
 *   - Left identity:  `Ply.pure(a).flatMap(f)` = `f(a)`
 *   - Right identity: `m.flatMap(Ply.pure)` = `m`
 *   - Associativity:  `(m.flatMap(f)).flatMap(g)` = `m.flatMap(a => f(a).flatMap(g))`
 */
opaque type Ply[A] = GameState => Ply.Step[A]

object Ply:

  /** The outcome of running one Ply step. */
  enum Step[+A]:
    /** Game is ongoing; `value` is the step result, `state` the updated game. */
    case Active(value: A, state: GameState) extends Step[A]
    /** Game has ended; `result` names the outcome, `state` is the terminal position. */
    case Terminal(result: GameResult, state: GameState) extends Step[Nothing]

  /** Lifts `a` into Ply without modifying game state. */
  def pure[A](a: A): Ply[A] = state => Step.Active(a, state)

  /** Reads the current [[GameState]] as the result value. */
  val get: Ply[GameState] = state => Step.Active(state, state)

  /** Replaces the current game state. */
  def set(s: GameState): Ply[Unit] = _ => Step.Active((), s)

  /** Terminates the computation with `result`, preserving the state at that point. */
  def end[A](result: GameResult): Ply[A] = state => Step.Terminal(result, state)

  extension [A](ply: Ply[A])

    /** Runs this computation from `initial`. */
    def run(initial: GameState): Step[A] = ply(initial)

    /** Returns the game state after running from `initial`, regardless of whether the game ended. */
    def finalState(initial: GameState): GameState = run(initial) match
      case Step.Active(_, s)   => s
      case Step.Terminal(_, s) => s

    def map[B](f: A => B): Ply[B] = state =>
      ply(state) match
        case Step.Active(a, s) => Step.Active(f(a), s)
        case t: Step.Terminal  => t

    /** Sequences `f` after this computation; skips `f` if the game has already ended. */
    def flatMap[B](f: A => Ply[B]): Ply[B] = state =>
      ply(state) match
        case Step.Active(a, s) => f(a)(s)
        case t: Step.Terminal  => t
