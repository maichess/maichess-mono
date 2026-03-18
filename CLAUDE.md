# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

```bash
sbt compile          # Compile
sbt run              # Run the application
sbt test             # Run all tests
sbt "testOnly *Foo"  # Run a single test class by name pattern
sbt scalafix         # Run Scalafix linter/rewriter
```

WartRemover static analysis runs automatically during `sbt compile` (all `Warts.unsafe` are errors).

## Architecture

- **Language**: Scala 3.8.2, **Build**: sbt 1.12.6
- **Package root**: `org.maichess.mono`
- **Linters**: WartRemover (`Warts.unsafe` as compile errors) + Scalafix
- Entry point: `src/main/scala/main.scala`

## Code Style

- Follow functional programming principles.
- Functions should do one thing. If you need "and" to describe it, split it.
- Max ~15–20 lines per function. Decompose if longer.
- Names must express intent without comments.
- Do not write comments unless absolutely necessary (e.g., explaining non-obvious algorithms or critical warnings).
- Prefer early returns over nested conditionals.
- Before finishing, scan for duplicated logic and extract it.
