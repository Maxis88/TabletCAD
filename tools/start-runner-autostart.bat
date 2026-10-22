@echo off
rem Starts the TabletCAD test runner at Windows logon (copy a shortcut of this file to shell:startup).
rem Runs minimized, below-normal priority, without the idle stop.
cd /d "%~dp0.."
start "TabletCAD runner" /min powershell -NoProfile -ExecutionPolicy Bypass -File tools\TabletCAD-TestRunner.ps1 -IdleMinutes 0
