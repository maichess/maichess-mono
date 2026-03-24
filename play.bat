@echo off
call sbt "uiText/assembly"
if %errorlevel% neq 0 exit /b %errorlevel%
javaw -jar modules\ui-text\target\scala-3.8.2\maichess.jar
