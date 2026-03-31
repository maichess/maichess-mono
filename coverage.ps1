$ScalaVersion = "3.8.2"
$ReportFile   = "coverage-report.html"
$Modules      = @("model", "rules", "engine", "bots")

Write-Host "=== Running coverage build ==="
$ErrorActionPreference = "Continue"
& sbt clean coverage test
if ($LASTEXITCODE -ne 0) { Write-Host "Tests failed, generating reports anyway..." }

# coverageReport may fail on minimum thresholds but still generates reports
& sbt coverageReport 2>$null
$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== Generating coverage dashboard ==="

$rows    = @()
$totalS  = 0
$totalI  = 0

foreach ($mod in $Modules) {
    $xml = "modules\$mod\target\scala-$ScalaVersion\scoverage-report\scoverage.xml"
    if (-not (Test-Path $xml)) {
        Write-Host "  SKIP  $mod (no report)"
        continue
    }
    [xml]$doc = Get-Content $xml
    $sc = [int]$doc.scoverage.'statement-count'
    $si = [int]$doc.scoverage.'statements-invoked'
    $sr = $doc.scoverage.'statement-rate'
    $br = $doc.scoverage.'branch-rate'

    $totalS += $sc
    $totalI += $si
    $rows += [pscustomobject]@{ Name=$mod; SC=$sc; SI=$si; SR=$sr; BR=$br }
    Write-Host "  ${mod}: stmt=${sr}%  branch=${br}%"
}

$overall = if ($totalS -gt 0) { [math]::Round($totalI * 100 / $totalS, 2) } else { 0 }
Write-Host ""
Write-Host "  OVERALL: ${overall}%"

# ── colour helper ────────────────────────────────────────────────────────────
function Get-Color([double]$pct) {
    if ($pct -ge 90) { "green" } elseif ($pct -ge 70) { "yellow" } else { "red" }
}

# ── build HTML ───────────────────────────────────────────────────────────────
$ts      = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
$oColor  = Get-Color $overall
$modRows = ""

foreach ($r in $rows) {
    $bc   = Get-Color ([double]$r.SR)
    $link = "modules/$($r.Name)/target/scala-$ScalaVersion/scoverage-report/index.html"
    $modRows += @"
<tr>
  <td><strong>$($r.Name)</strong></td>
  <td>$($r.SR)%</td>
  <td class="bar-cell"><div class="bar-bg"><div class="bar-fg $bc" style="width:$($r.SR)%"></div></div></td>
  <td>$($r.BR)%</td>
  <td>$($r.SI)/$($r.SC)</td>
  <td><a href="$link">Details &rarr;</a></td>
</tr>
"@
}

$html = @"
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
<p class="timestamp">Generated: $ts</p>
<div class="overall $oColor">Overall Statement Coverage: <strong>${overall}%</strong> &mdash; ${totalI}/${totalS} statements</div>
<table>
<tr><th>Module</th><th>Statement %</th><th class="bar-cell">Coverage</th><th>Branch %</th><th>Statements</th><th>Report</th></tr>
$modRows
</table>
</body>
</html>
"@

$html | Out-File -Encoding utf8 $ReportFile

Write-Host ""
Write-Host "=== Dashboard written to $ReportFile ==="
Start-Process $ReportFile
