param(
    [Parameter(Position = 0)]
    [string]$Version = "release",

    [string]$JavaHome = "D:\work-tools\jdk-21.0.2"
)

$ErrorActionPreference = "Stop"

function Invoke-CheckedCommand {
    param(
        [string]$Command,
        [string[]]$Arguments,
        [string]$Description
    )

    Write-Host ""
    Write-Host "==> $Description" -ForegroundColor Cyan
    & $Command @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "$Description 失败，退出码：$LASTEXITCODE"
    }
}

function Assert-CommandExists {
    param(
        [string]$Command,
        [string]$Description
    )

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "未找到$Description，请先安装并加入环境变量：$Command"
    }
}

function Set-BuildJavaHome {
    param(
        [string]$RequestedJavaHome,
        [string]$OriginalPath
    )

    $EffectiveJavaHome = $RequestedJavaHome
    if ([string]::IsNullOrWhiteSpace($EffectiveJavaHome)) {
        $EffectiveJavaHome = $env:CRM_BUILD_JAVA_HOME
    }
    if ([string]::IsNullOrWhiteSpace($EffectiveJavaHome)) {
        $EffectiveJavaHome = $env:JAVA_HOME
    }
    if ([string]::IsNullOrWhiteSpace($EffectiveJavaHome)) {
        throw "未指定JDK21目录，请使用-JavaHome参数或CRM_BUILD_JAVA_HOME环境变量"
    }
    if (-not (Test-Path -LiteralPath $EffectiveJavaHome -PathType Container)) {
        throw "JDK目录不存在：$EffectiveJavaHome"
    }

    $ResolvedJavaHome = (Resolve-Path -LiteralPath $EffectiveJavaHome).Path
    $JavaExecutable = Join-Path $ResolvedJavaHome "bin\java.exe"
    if (-not (Test-Path -LiteralPath $JavaExecutable -PathType Leaf)) {
        throw "JDK目录中没有找到java.exe：$JavaExecutable"
    }

    $env:JAVA_HOME = $ResolvedJavaHome
    $env:Path = "$(Join-Path $ResolvedJavaHome "bin");$OriginalPath"

    $JavaStartInfo = New-Object System.Diagnostics.ProcessStartInfo
    $JavaStartInfo.FileName = $JavaExecutable
    $JavaStartInfo.Arguments = "-version"
    $JavaStartInfo.UseShellExecute = $false
    $JavaStartInfo.RedirectStandardOutput = $true
    $JavaStartInfo.RedirectStandardError = $true
    $JavaStartInfo.CreateNoWindow = $true
    $JavaProcess = New-Object System.Diagnostics.Process
    $JavaProcess.StartInfo = $JavaStartInfo
    try {
        [void]$JavaProcess.Start()
        $JavaStandardOutput = $JavaProcess.StandardOutput.ReadToEnd()
        $JavaStandardError = $JavaProcess.StandardError.ReadToEnd()
        $JavaProcess.WaitForExit()
        $JavaExitCode = $JavaProcess.ExitCode
    } finally {
        $JavaProcess.Dispose()
    }
    $JavaVersionOutput = "$JavaStandardOutput`n$JavaStandardError".Trim()
    if ($JavaExitCode -ne 0) {
        throw "JDK版本检查失败：$ResolvedJavaHome"
    }
    if ($JavaVersionOutput -notmatch 'version\s+"21(?:\.|")') {
        throw "当前构建JDK不是21：$JavaVersionOutput"
    }

    $MavenVersionOutput = (& mvn -version 2>&1 | Out-String)
    if ($LASTEXITCODE -ne 0) {
        throw "Maven版本检查失败"
    }
    if ($MavenVersionOutput -notmatch 'Java version:\s*21(?:\.|,)') {
        throw "Maven没有使用JDK21：$MavenVersionOutput"
    }

    Write-Host ""
    Write-Host "构建JDK：$ResolvedJavaHome" -ForegroundColor Green
    Write-Host $MavenVersionOutput.Trim()
}

if ($Version -notmatch "^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$") {
    throw "镜像版本格式不正确，只允许字母、数字、下划线、点和短横线"
}

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$DeployDir = Split-Path -Parent $ScriptDir
$RootDir = Split-Path -Parent $DeployDir
$BackendDir = Join-Path $RootDir "backend"
$FrontendDir = Join-Path $RootDir "frontend"
$DockerDir = Join-Path $DeployDir "docker"
$OutputDir = Join-Path $RootDir "release\app-images\$Version"
$ContextBaseDir = Join-Path $RootDir ".build\app-images"
$ContextDir = Join-Path $ContextBaseDir ([Guid]::NewGuid().ToString("N"))
$BackendContextDir = Join-Path $ContextDir "backend"
$FrontendContextDir = Join-Path $ContextDir "frontend"
$FrontendBuildDir = Join-Path $ContextDir "frontend-build"
$BackendImage = "crm-backend:$Version"
$FrontendImage = "crm-frontend:$Version"
$BackendArchive = Join-Path $OutputDir "crm-backend-$Version.tar"
$FrontendArchive = Join-Path $OutputDir "crm-frontend-$Version.tar"
$OriginalJavaHome = $env:JAVA_HOME
$OriginalPath = $env:Path

Assert-CommandExists -Command "mvn" -Description "Maven"
Assert-CommandExists -Command "npm" -Description "Node.js和npm"
Assert-CommandExists -Command "docker" -Description "Docker"

if (Get-Command "nvm" -ErrorAction SilentlyContinue) {
    Invoke-CheckedCommand `
        -Command "nvm" `
        -Arguments @("use", "22.22.2") `
        -Description "切换Node.js版本"
}

Invoke-CheckedCommand `
    -Command "docker" `
    -Arguments @("info") `
    -Description "检查Docker运行状态"

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
New-Item -ItemType Directory -Force -Path $BackendContextDir | Out-Null
New-Item -ItemType Directory -Force -Path $FrontendContextDir | Out-Null
New-Item -ItemType Directory -Force -Path $FrontendBuildDir | Out-Null

try {
    Set-BuildJavaHome -RequestedJavaHome $JavaHome -OriginalPath $OriginalPath

    Push-Location $BackendDir
    try {
        Invoke-CheckedCommand `
            -Command "mvn" `
            -Arguments @("-DskipTests", "-pl", "crm-web", "-am", "clean", "package") `
            -Description "构建后端Jar"
    } finally {
        Pop-Location
    }

    $BackendJar = Get-ChildItem `
        -LiteralPath (Join-Path $BackendDir "crm-web\target") `
        -Filter "crm-web-*.jar" |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $BackendJar) {
        throw "未找到后端Jar：backend\crm-web\target\crm-web-*.jar"
    }

    $FrontendBuildFiles = @(
        "package.json",
        "package-lock.json",
        "index.html",
        "vite.config.js",
        ".env.production"
    )
    foreach ($FrontendBuildFile in $FrontendBuildFiles) {
        $SourceFile = Join-Path $FrontendDir $FrontendBuildFile
        if (Test-Path -LiteralPath $SourceFile -PathType Leaf) {
            Copy-Item -LiteralPath $SourceFile -Destination (Join-Path $FrontendBuildDir $FrontendBuildFile)
        }
    }
    Copy-Item `
        -LiteralPath (Join-Path $FrontendDir "src") `
        -Destination (Join-Path $FrontendBuildDir "src") `
        -Recurse
    Copy-Item `
        -LiteralPath (Join-Path $FrontendDir "public") `
        -Destination (Join-Path $FrontendBuildDir "public") `
        -Recurse

    Push-Location $FrontendBuildDir
    try {
        if (Test-Path -LiteralPath (Join-Path $FrontendBuildDir "package-lock.json")) {
            Invoke-CheckedCommand `
                -Command "npm" `
                -Arguments @("ci", "--prefer-offline", "--no-audit", "--no-fund") `
                -Description "在隔离目录安装前端依赖"
        } else {
            Invoke-CheckedCommand `
                -Command "npm" `
                -Arguments @("install", "--prefer-offline", "--no-audit", "--no-fund") `
                -Description "在隔离目录安装前端依赖"
        }
        Invoke-CheckedCommand `
            -Command "npm" `
            -Arguments @("run", "build") `
            -Description "构建前端静态资源"
    } finally {
        Pop-Location
    }

    $FrontendDistDir = Join-Path $FrontendBuildDir "dist"
    if (-not (Test-Path -LiteralPath (Join-Path $FrontendDistDir "index.html"))) {
        throw "未找到前端构建产物：frontend\dist\index.html"
    }

    Copy-Item -LiteralPath $BackendJar.FullName -Destination (Join-Path $BackendContextDir "app.jar")
    Copy-Item `
        -LiteralPath (Join-Path $DockerDir "backend\Dockerfile") `
        -Destination (Join-Path $BackendContextDir "Dockerfile")

    Copy-Item -LiteralPath $FrontendDistDir -Destination (Join-Path $FrontendContextDir "dist") -Recurse
    Copy-Item `
        -LiteralPath (Join-Path $DockerDir "frontend\Dockerfile") `
        -Destination (Join-Path $FrontendContextDir "Dockerfile")
    Copy-Item `
        -LiteralPath (Join-Path $DockerDir "frontend\nginx.conf") `
        -Destination (Join-Path $FrontendContextDir "nginx.conf")
    Copy-Item `
        -LiteralPath (Join-Path $DockerDir "frontend\write-runtime-config.sh") `
        -Destination (Join-Path $FrontendContextDir "write-runtime-config.sh")

    Invoke-CheckedCommand `
        -Command "docker" `
        -Arguments @("build", "--pull=false", "-t", $BackendImage, $BackendContextDir) `
        -Description "构建后端Docker镜像"

    Invoke-CheckedCommand `
        -Command "docker" `
        -Arguments @("build", "--pull=false", "-t", $FrontendImage, $FrontendContextDir) `
        -Description "构建前端Docker镜像"

    Invoke-CheckedCommand `
        -Command "docker" `
        -Arguments @("save", "-o", $BackendArchive, $BackendImage) `
        -Description "导出后端镜像"

    Invoke-CheckedCommand `
        -Command "docker" `
        -Arguments @("save", "-o", $FrontendArchive, $FrontendImage) `
        -Description "导出前端镜像"

    $BackendHash = Get-FileHash -LiteralPath $BackendArchive -Algorithm SHA256
    $FrontendHash = Get-FileHash -LiteralPath $FrontendArchive -Algorithm SHA256
    $ChecksumFile = Join-Path $OutputDir "SHA256SUMS.txt"
    @(
        "$($BackendHash.Hash.ToLower())  $($BackendHash.Path | Split-Path -Leaf)"
        "$($FrontendHash.Hash.ToLower())  $($FrontendHash.Path | Split-Path -Leaf)"
    ) | Set-Content -LiteralPath $ChecksumFile -Encoding ASCII

    Write-Host ""
    Write-Host "构建完成" -ForegroundColor Green
    Write-Host "后端镜像：$BackendImage"
    Write-Host "前端镜像：$FrontendImage"
    Write-Host "输出目录：$OutputDir"
    Write-Host ""
    Write-Host "上传以下文件到服务器：" -ForegroundColor Yellow
    Write-Host "  $BackendArchive"
    Write-Host "  $FrontendArchive"
    Write-Host ""
    Write-Host "服务器执行：" -ForegroundColor Yellow
    Write-Host "  docker load -i crm-backend-$Version.tar"
    Write-Host "  docker load -i crm-frontend-$Version.tar"
    Write-Host "  docker compose up -d --no-deps --force-recreate crm-backend crm-frontend"
    Write-Host ""
    Write-Host "注意：Version必须与服务器.env中的CRM_VERSION一致。" -ForegroundColor Yellow
} finally {
    $env:JAVA_HOME = $OriginalJavaHome
    $env:Path = $OriginalPath

    $ResolvedContextBase = [System.IO.Path]::GetFullPath($ContextBaseDir)
    $ResolvedContext = [System.IO.Path]::GetFullPath($ContextDir)
    if ($ResolvedContext.StartsWith($ResolvedContextBase, [System.StringComparison]::OrdinalIgnoreCase) `
            -and (Test-Path -LiteralPath $ResolvedContext)) {
        Remove-Item -LiteralPath $ResolvedContext -Recurse -Force
    }
}
