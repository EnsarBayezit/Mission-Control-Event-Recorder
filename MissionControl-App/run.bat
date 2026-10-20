@echo off
title Mission Control
cd /d "%~dp0"
if exist MissionControl.exe (
    start MissionControl.exe
) else (
    start javaw -Djava.library.path=lib -cp "lib/*" MissionControl
)
