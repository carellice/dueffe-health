@echo off
rem Versione Windows di release-apk.command: stessa logica, stessa chiave in .secrets\.
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"
set "ROOT=%~dp0"
set "ROOT=%ROOT:~0,-1%"
set "PUBLISHED=1"

if not defined ANDROID_HOME set "ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk"
if not defined JAVA_HOME if exist "%ProgramFiles%\Android\Android Studio\jbr\bin\java.exe" set "JAVA_HOME=%ProgramFiles%\Android\Android Studio\jbr"
set "KEYTOOL=keytool"
if defined JAVA_HOME set "KEYTOOL=%JAVA_HOME%\bin\keytool"

set "REPO=%RELEASE_REPO%"
if not defined REPO set "REPO=carellice/android-app-releases"
echo %REPO%| findstr /r "^[A-Za-z0-9_.-][A-Za-z0-9_.-]*/[A-Za-z0-9_.-][A-Za-z0-9_.-]*$" >nul || (call :fail "RELEASE_REPO deve essere owner/repo" & exit /b 1)
where gh >nul 2>&1 || (call :fail "Installa GitHub CLI: gh" & exit /b 1)
"%KEYTOOL%" -help >nul 2>&1 || (call :fail "keytool non disponibile: installa un JDK o imposta JAVA_HOME" & exit /b 1)
gh auth status >nul 2>&1 || (call :fail "Autenticazione GitHub non valida. Esegui gh auth login." & exit /b 1)
gh repo view "%REPO%" --json nameWithOwner >nul || (call :fail "Repository %REPO% non accessibile" & exit /b 1)
set "PYTHON=python"
where python >nul 2>&1 || set "PYTHON=py -3"

set "VERSION_FILE=%ROOT%\version.properties"
for /f "usebackq tokens=1,* delims==" %%a in ("%VERSION_FILE%") do (
  if "%%a"=="VERSION_CODE" set "CODE=%%b"
  if "%%a"=="VERSION_NAME" set "NAME=%%b"
)
echo %CODE%| findstr /r "^[0-9][0-9]*$" >nul || (call :fail "version.properties non valido" & exit /b 1)
echo %NAME%| findstr /r "^[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*$" >nul || (call :fail "version.properties non valido" & exit /b 1)
for /f "tokens=1-3 delims=." %%a in ("%NAME%") do (set "MAJOR=%%a" & set "MINOR=%%b" & set "PATCH=%%c")
set /a NEXT_CODE=CODE+1, NEXT_PATCH=PATCH+1
set "NEXT_NAME=%MAJOR%.%MINOR%.%NEXT_PATCH%"
set "TAG=health-v%NEXT_NAME%"
gh release view "%TAG%" --repo "%REPO%" >nul 2>&1 && (call :fail "Release gia' esistente: %TAG%" & exit /b 1)

rem Chiave di firma: creata al primo uso, poi riusata per tutti gli aggiornamenti.
if not exist "%ROOT%\.secrets" mkdir "%ROOT%\.secrets"
if not exist "%ROOT%\dist" mkdir "%ROOT%\dist"
if not exist "%ROOT%\.secrets\signing.properties" (
  for /f "delims=" %%p in ('powershell -NoProfile -Command "$b = New-Object byte[] 24; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); -join ($b | ForEach-Object { $_.ToString('x2') })"') do set "PASS=%%p"
  if not defined PASS call :fail "Generazione password non riuscita" & exit /b 1
  "%KEYTOOL%" -genkeypair -v -keystore "%ROOT%\.secrets\health-release.jks" -alias health -keyalg RSA -keysize 3072 -validity 10000 -storepass "!PASS!" -keypass "!PASS!" -dname "CN=Health" >nul 2>&1 || (call :fail "Creazione chiave non riuscita" & exit /b 1)
  > "%ROOT%\.secrets\signing.properties" echo storeFile=.secrets/health-release.jks
  >> "%ROOT%\.secrets\signing.properties" echo storePassword=!PASS!
  >> "%ROOT%\.secrets\signing.properties" echo keyAlias=health
  >> "%ROOT%\.secrets\signing.properties" echo keyPassword=!PASS!
  echo Creata chiave .secrets\health-release.jks: conservala per i prossimi aggiornamenti.
)

copy /y "%VERSION_FILE%" "%TEMP%\health-version.bak" >nul
> "%VERSION_FILE%" echo VERSION_CODE=%NEXT_CODE%
>> "%VERSION_FILE%" echo VERSION_NAME=%NEXT_NAME%
set "PUBLISHED=0"

echo Compilo Health %NEXT_NAME%...
call "%ROOT%\gradlew.bat" :app:assembleRelease "-PupdateRepository=%REPO%" || (call :fail "Compilazione release non riuscita" & exit /b 1)
set "APK=%ROOT%\app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" call :fail "APK release non creato" & exit /b 1
set "ASSET=%ROOT%\dist\health-%NEXT_NAME%.apk"
copy /y "%APK%" "%ASSET%" >nul
%PYTHON% "%ROOT%\write-update-metadata.py" "%ASSET%" %NEXT_CODE% %NEXT_NAME% it.dueffe.health || (call :fail "Metadati di aggiornamento non generati" & exit /b 1)

echo Pubblico %TAG% in %REPO%...
gh release create "%TAG%" "%ASSET%" "%ROOT%\dist\latest.json" --repo "%REPO%" --title "Health %NEXT_NAME%" --notes "APK Android Health %NEXT_NAME%" || (call :fail "Pubblicazione non riuscita" & exit /b 1)
rem Da qui la release esiste: la nuova versione resta salvata anche se la pulizia fallisce.
set "PUBLISHED=1"

echo Release %TAG% pubblicata. Pulizia delle vecchie release Health...
gh api --paginate "repos/%REPO%/releases?per_page=100" --jq ".[].tag_name" > "%TEMP%\health-tags-lf.txt" || (call :fail "Pubblicazione riuscita, impossibile elencare le vecchie release: controllale manualmente." & exit /b 1)
rem gh scrive righe con solo LF: "more" le converte in CRLF, come si aspetta findstr.
more < "%TEMP%\health-tags-lf.txt" > "%TEMP%\health-tags.txt"
for /f "usebackq delims=" %%t in (`findstr /r /x "health-v[0-9][0-9]*\.[0-9][0-9]*\.[0-9][0-9]*" "%TEMP%\health-tags.txt"`) do (
  if not "%%t"=="%TAG%" (
    gh release delete "%%t" --repo "%REPO%" --cleanup-tag --yes || (call :fail "Pubblicazione riuscita, pulizia interrotta su %%t." & exit /b 1)
  )
)
echo Completato: %REPO% / %TAG%
if not defined HEALTH_NO_PAUSE pause
exit /b 0

:fail
echo Errore: %~1 1>&2
if "%PUBLISHED%"=="0" copy /y "%TEMP%\health-version.bak" "%VERSION_FILE%" >nul
if not defined HEALTH_NO_PAUSE pause
exit /b 1
