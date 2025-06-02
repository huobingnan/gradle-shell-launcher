SET "BIN_DIR=%~dp0"
PUSHD %BIN_DIR%
SET "BIN_DIR=%CD%"
POPD
FOR /F "delims=" %%i in ("%BIN_DIR%\..") DO SET "APP_HOME=%%~fi"
:: ------------------------------- JRE HOME ------------------------------
SET JRE=
IF "%JRE%" == "" (
  IF EXIST "%JDK_HOME%" (
    SET "JRE=%JDK_HOME%"
  ) ELSE IF EXIST "%JAVA_HOME%" (
    SET "JRE=%JAVA_HOME%"
  )
)
SET "JAVA_EXE=%JRE%\bin\java.exe"
IF NOT EXIST "%JAVA_EXE%" (
  ECHO ERROR: cannot start %APP_NAME%.
  ECHO No JRE found. Please make sure JDK_HOME, or JAVA_HOME point to a valid JRE installation.
  EXIT /B
)