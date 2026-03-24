#!/usr/bin/env bash
set -euo pipefail

SCALA_VERSION="3.8.2"
REPORT_FILE="coverage-report.html"
MODULES=("model" "rules" "engine")

echo "=== Running coverage build ==="
sbt clean coverage test
# coverageReport may fail on minimum thresholds, but still generates reports
sbt coverageReport 2>&1 || true

echo ""
echo "=== Generating coverage dashboard ==="

# ── Extract coverage data from scoverage.xml per module ──────────────────────
declare -a MOD_NAMES=()
declare -a MOD_STMTS=()
declare -a MOD_INVOKED=()
declare -a MOD_STMT_RATE=()
declare -a MOD_BRANCH_RATE=()

total_stmts=0
total_invoked=0

for mod in "${MODULES[@]}"; do
  xml="modules/$mod/target/scala-$SCALA_VERSION/scoverage-report/scoverage.xml"
  if [ ! -f "$xml" ]; then
    echo "  SKIP  $mod (no report)"
    continue
  fi

  stmt_count=$(grep -oP 'statement-count="\K[0-9]+' "$xml" | head -1)
  stmt_invoked=$(grep -oP 'statements-invoked="\K[0-9]+' "$xml" | head -1)
  stmt_rate=$(grep -oP 'statement-rate="\K[0-9.]+' "$xml" | head -1)
  branch_rate=$(grep -oP 'branch-rate="\K[0-9.]+' "$xml" | head -1)

  MOD_NAMES+=("$mod")
  MOD_STMTS+=("$stmt_count")
  MOD_INVOKED+=("$stmt_invoked")
  MOD_STMT_RATE+=("$stmt_rate")
  MOD_BRANCH_RATE+=("$branch_rate")

  total_stmts=$((total_stmts + stmt_count))
  total_invoked=$((total_invoked + stmt_invoked))

  echo "  $mod: stmt=${stmt_rate}%  branch=${branch_rate}%"
done

if [ "$total_stmts" -gt 0 ]; then
  overall=$(echo "scale=2; $total_invoked * 100 / $total_stmts" | bc)
else
  overall="0.00"
fi
echo ""
echo "  OVERALL: ${overall}%"

# ── Generate HTML dashboard ─────────────────────────────────────────────────
{
cat <<'HEADER'
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>MaiChess Coverage Report</title>
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; }
  body { font-family: system-ui, -apple-system, sans-serif; background: #f5f5f5; color: #222; padding: 2rem; }
  h1 { margin-bottom: .5rem; }
  .timestamp { color: #666; margin-bottom: 1.5rem; }
  .overall { font-size: 1.4rem; margin: 1rem 0 2rem; padding: 1rem; background: #fff; border-radius: 8px;
             border-left: 6px solid; }
  .overall.green  { border-color: #2ecc71; }
  .overall.yellow { border-color: #f1c40f; }
  .overall.red    { border-color: #e74c3c; }
  table { width: 100%; border-collapse: collapse; background: #fff; border-radius: 8px; overflow: hidden; }
  th, td { padding: .75rem 1rem; text-align: left; border-bottom: 1px solid #eee; }
  th { background: #333; color: #fff; }
  .bar-cell { width: 40%; }
  .bar-bg { background: #eee; border-radius: 4px; height: 22px; position: relative; }
  .bar-fg { height: 100%; border-radius: 4px; }
  .bar-fg.green  { background: #2ecc71; }
  .bar-fg.yellow { background: #f1c40f; }
  .bar-fg.red    { background: #e74c3c; }
  a { color: #2980b9; text-decoration: none; }
  a:hover { text-decoration: underline; }
</style>
</head>
<body>
<h1>MaiChess Code Coverage</h1>
HEADER

echo "<p class=\"timestamp\">Generated: $(date '+%Y-%m-%d %H:%M:%S')</p>"

# Overall score
if (( $(echo "$overall >= 90" | bc -l) )); then
  color="green"
elif (( $(echo "$overall >= 70" | bc -l) )); then
  color="yellow"
else
  color="red"
fi
echo "<div class=\"overall $color\">Overall Statement Coverage: <strong>${overall}%</strong> &mdash; ${total_invoked}/${total_stmts} statements</div>"

# Per-module table
cat <<'TABLE_HEAD'
<table>
<tr><th>Module</th><th>Statement %</th><th class="bar-cell">Coverage</th><th>Branch %</th><th>Statements</th><th>Report</th></tr>
TABLE_HEAD

for i in "${!MOD_NAMES[@]}"; do
  mod="${MOD_NAMES[$i]}"
  sr="${MOD_STMT_RATE[$i]}"
  br="${MOD_BRANCH_RATE[$i]}"
  sc="${MOD_STMTS[$i]}"
  si="${MOD_INVOKED[$i]}"
  link="modules/$mod/target/scala-$SCALA_VERSION/scoverage-report/index.html"

  if (( $(echo "$sr >= 90" | bc -l) )); then
    barcolor="green"
  elif (( $(echo "$sr >= 70" | bc -l) )); then
    barcolor="yellow"
  else
    barcolor="red"
  fi

  echo "<tr>"
  echo "  <td><strong>$mod</strong></td>"
  echo "  <td>${sr}%</td>"
  echo "  <td class=\"bar-cell\"><div class=\"bar-bg\"><div class=\"bar-fg $barcolor\" style=\"width:${sr}%\"></div></div></td>"
  echo "  <td>${br}%</td>"
  echo "  <td>${si}/${sc}</td>"
  echo "  <td><a href=\"$link\">Details &rarr;</a></td>"
  echo "</tr>"
done

cat <<'FOOTER'
</table>
</body>
</html>
FOOTER
} > "$REPORT_FILE"

echo ""
echo "=== Dashboard written to $REPORT_FILE ==="

# Open in default browser if possible
if command -v open &>/dev/null; then
  open "$REPORT_FILE"
elif command -v xdg-open &>/dev/null; then
  xdg-open "$REPORT_FILE"
fi
