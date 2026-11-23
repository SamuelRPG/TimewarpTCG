@echo off
rem Arranca el backend contra SUPABASE. Necesita el fichero supabase.env.cmd (copia de supabase.env.cmd.example) con tus datos.
if not exist "%~dp0supabase.env.cmd" (
  echo Falta supabase.env.cmd. Copia supabase.env.cmd.example como supabase.env.cmd y rellena tus datos.
  pause
  exit /b 1
)
call "%~dp0supabase.env.cmd"
cd /d "%~dp0TimewarpTCG.Back"
call mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run "-Dspring-boot.run.profiles=supabase"
pause
