package chess.ui

import chess.engine.GameController
import chess.rules.StandardRules

// $COVERAGE-OFF$ — thin entry-point; logic lives in TextUI and is fully covered there
@main def run(): Unit = TextUI.play(GameController(StandardRules), TextUI.fromStdin)
// $COVERAGE-ON$
