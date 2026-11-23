@echo off
rem Arranca el backend en modo demo (base de datos H2 en memoria, sin Supabase). Cierralo con Ctrl+C.
cd /d "%~dp0TimewarpTCG.Back"
call mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run
pause
