@echo off
setlocal
cd /d "%~dp0"
if not exist "target\terraforming-mars-telemetry-1.0.30.jar" (
  echo Building Terraforming Mars Telemetry...
  mvn -q package -DskipTests
  if errorlevel 1 (
    echo Build failed. Install Java 21 and Maven, then run this again.
    pause
    exit /b 1
  )
)
java -jar "%~dp0target\terraforming-mars-telemetry-1.0.30.jar" %*
