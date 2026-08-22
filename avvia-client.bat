@echo off
setlocal
title Arise - client di sviluppo
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"

if not exist "%JAVA_HOME%\bin\java.exe" goto :niente_java
if not exist "gradlew.bat" goto :cartella_sbagliata

echo.
echo  ================================================================
echo   Avvio di Minecraft con la mod. Ricompila da solo: non serve
echo   fare build prima. La prima volta ci mette qualche minuto.
echo.
echo   Nessun login: entra con un account fittizio.
echo   Per fermarlo, chiudi la finestra del gioco.
echo  ================================================================
echo.

call "%~dp0gradlew.bat" runClient

echo.
echo  Il gioco si e' chiuso.
pause
exit /b 0

:niente_java
echo.
echo  Non trovo Java 25 in:
echo    %JAVA_HOME%
echo  Se lo hai altrove, correggi la riga JAVA_HOME dentro questo file.
pause
exit /b 1

:cartella_sbagliata
echo.
echo  Questo file va lasciato nella cartella del progetto, accanto a gradlew.bat.
pause
exit /b 1
