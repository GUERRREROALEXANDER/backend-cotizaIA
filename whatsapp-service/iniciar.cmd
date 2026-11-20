@echo off
rem Doble clic para iniciar el asistente de WhatsApp de CotizaIA.
cd /d "%~dp0"
where node >nul 2>nul || (echo Falta Node.js: instalelo desde https://nodejs.org & pause & exit /b 1)
if not exist node_modules (
  echo Instalando dependencias por primera vez...
  call npm install --no-fund --no-audit || (pause & exit /b 1)
)
node iniciar.mjs %*
pause
