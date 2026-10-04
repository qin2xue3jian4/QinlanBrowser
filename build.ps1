param([string[]]$Tasks = @(':app:assembleRelease', ':app:testDebugUnitTest', ':app:lintDebug'))
$ErrorActionPreference = 'Stop'
$buildTemp = Join-Path $PSScriptRoot '.tmp'
New-Item -ItemType Directory -Force -Path $buildTemp | Out-Null
$priorJavaOptions = $env:JAVA_TOOL_OPTIONS
try {
    $env:JAVA_TOOL_OPTIONS = "$priorJavaOptions -Djava.io.tmpdir=$buildTemp -Djdk.net.unixdomain.tmpdir=$buildTemp"
    & (Join-Path $PSScriptRoot 'gradlew.bat') @Tasks --console=plain
    if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
} finally { $env:JAVA_TOOL_OPTIONS = $priorJavaOptions }
