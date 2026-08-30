# Sika check — lint + unit tests.
# Must pass before anything is installed on the phone. PROFILE.md section 12.

$ErrorActionPreference = 'Continue'

# gradlew.bat needs a JVM to launch itself. gradle.properties' org.gradle.java.home only
# picks the JVM the build runs on, which is too late — so point JAVA_HOME at the
# JetBrains Runtime that ships with Android Studio. Set it here rather than relying on
# the shell, so `check` behaves the same from a terminal, a script, or an agent.
if (-not $env:JAVA_HOME) {
    $jbr = 'C:\Program Files\Android\Android Studio\jbr'
    if (Test-Path "$jbr\bin\java.exe") {
        $env:JAVA_HOME = $jbr
    } else {
        Write-Host "check: FAIL - no JAVA_HOME and no JBR at $jbr" -ForegroundColor Red
        Write-Host 'Install Android Studio, or set JAVA_HOME to a JDK 17+.'
        exit 1
    }
}

Write-Host 'Sika check: lint + unit tests' -ForegroundColor Cyan
Write-Host "JAVA_HOME: $env:JAVA_HOME"

& .\gradlew.bat lint testDebugUnitTest
$code = $LASTEXITCODE

if ($code -ne 0) {
    Write-Host ''
    Write-Host 'check: FAIL' -ForegroundColor Red
    Write-Host 'Lint report: app\build\reports\lint-results-debug.html'
    Write-Host 'Test report: app\build\reports\tests\testDebugUnitTest\index.html'
    exit $code
}

Write-Host ''
Write-Host 'check: PASS' -ForegroundColor Green
exit 0
