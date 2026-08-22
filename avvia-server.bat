@echo off
setlocal
title Arise - server di sviluppo
cd /d "%~dp0"

set "JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"

if not exist "%JAVA_HOME%\bin\java.exe" goto :niente_java
if not exist "gradlew.bat" goto :cartella_sbagliata
if not exist "run" mkdir "run"

rem ---------------------------------------------------------------- EULA
rem La accetti tu cliccando, non questo file al posto tuo.
if not exist "run\eula.txt" goto :chiedi_eula
findstr /i /c:"eula=true" "run\eula.txt" >nul
if errorlevel 1 goto :chiedi_eula
goto :eula_ok

:chiedi_eula
echo.
echo   Un server di Minecraft parte solo se accetti l'EULA di Mojang:
echo     https://aka.ms/MinecraftEULA
echo.
set "RISPOSTA="
set /p "RISPOSTA=La accetti? [s/N] "
if /i not "%RISPOSTA%"=="s" goto :eula_rifiutata
> "run\eula.txt" echo eula=true
echo   Accettata. Scritta in run\eula.txt, non te lo chiedo piu'.

:eula_ok

rem ---------------------------------------------------------------- proprieta'
rem Solo se il file non c'e' gia': se lo hai modificato tu, resta com'e'.
if exist "run\server.properties" goto :proprieta_ok
> "run\server.properties" echo online-mode=false
echo   Creato run\server.properties con online-mode=false,
echo   altrimenti il client di sviluppo non riuscirebbe a entrare.

:proprieta_ok

echo.
echo  ================================================================
echo   Avvio del server. La prima volta ci mette qualche minuto.
echo.
echo   Quando compare "Done", in QUESTA finestra puoi scrivere comandi
echo   SENZA la barra davanti:
echo.
echo     op TUONOME                 ti da' i permessi da gamemaster
echo     arise city build rome      costruisce una citta'
echo     arise city build all       le costruisce tutte e cinque
echo     stop                       ferma il server
echo.
echo   Per giocarci: apri avvia-client.bat e connettiti a  localhost
echo  ================================================================
echo.

call "%~dp0gradlew.bat" runServer

echo.
echo  Il server si e' fermato.
pause
exit /b 0

:eula_rifiutata
echo.
echo  Senza EULA accettata il server non puo' partire. Non ho fatto niente.
pause
exit /b 1

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
