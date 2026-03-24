@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
cd /d "%SCRIPT_DIR%"

echo Clearing old coverage data...
for /d %%D in (modules\*\target\scala-*\scoverage-data)   do rd /s /q "%%D" 2>nul
for /d %%D in (modules\*\target\scala-*\scoverage-report) do rd /s /q "%%D" 2>nul
for /d %%D in (modules\*\target\scala-*\coverage-report)  do rd /s /q "%%D" 2>nul

echo Running tests with coverage...
sbt --no-server "coverage; tests/test; model/coverageReport; rules/coverageReport; engine/coverageReport; ui-text/coverageReport"

if errorlevel 1 (
  echo Coverage run failed.
) else (
  echo.
  echo Reports written to modules\^<module^>\target\scala-*\scoverage-report\index.html
)

pause
endlocal
