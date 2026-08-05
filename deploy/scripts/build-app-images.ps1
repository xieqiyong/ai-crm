param(
    [Parameter(Position = 0)]
    [string]$Version = "release",

    [string]$JavaHome = "D:\work-tools\jdk-21.0.2",

    [switch]$Upload,

    [switch]$DeployRemote,

    [string]$RemoteHost = "192.168.50.105",

    [string]$RemoteUser = "root",

    [string]$RemotePath = "/app/builds/products/crm",

    [string]$RemotePassword = $env:CRM_DEPLOY_PASSWORD,

    [string]$RemoteDeployDir = ""
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

function Write-Utf8NoBomFile {
    param(
        [string]$Path,
        [string]$Content
    )

    $Utf8NoBom = New-Object System.Text.UTF8Encoding($false)
    $NormalizedContent = $Content -replace "`r`n", "`n"
    [System.IO.File]::WriteAllText($Path, $NormalizedContent, $Utf8NoBom)
}

function New-ServerScripts {
    param(
        [string]$OutputDir,
        [string]$Version
    )

    $LoadScript = Join-Path $OutputDir "load-app-images.sh"
    $RestartScript = Join-Path $OutputDir "restart-crm-app.sh"
    $DeployScript = Join-Path $OutputDir "deploy-crm-app.sh"
    $VersionFile = Join-Path $OutputDir "VERSION"

    $LoadContent = @"
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="`$(cd "`$(dirname "`$0")" && pwd)"
CRM_VERSION="`${1:-$Version}"
BACKEND_ARCHIVE="`$SCRIPT_DIR/crm-backend-`$CRM_VERSION.tar"
MCP_ARCHIVE="`$SCRIPT_DIR/crm-mcp-`$CRM_VERSION.tar"
FRONTEND_ARCHIVE="`$SCRIPT_DIR/crm-frontend-`$CRM_VERSION.tar"

if [ ! -f "`$BACKEND_ARCHIVE" ]; then
  echo "未找到后端镜像：`$BACKEND_ARCHIVE"
  exit 1
fi

if [ ! -f "`$FRONTEND_ARCHIVE" ]; then
  echo "未找到前端镜像：`$FRONTEND_ARCHIVE"
  exit 1
fi

if [ ! -f "`$MCP_ARCHIVE" ]; then
  echo "未找到MCP服务镜像：`$MCP_ARCHIVE"
  exit 1
fi

echo "加载后端镜像：`$BACKEND_ARCHIVE"
docker load -i "`$BACKEND_ARCHIVE"

echo "加载MCP服务镜像：`$MCP_ARCHIVE"
docker load -i "`$MCP_ARCHIVE"

echo "加载前端镜像：`$FRONTEND_ARCHIVE"
docker load -i "`$FRONTEND_ARCHIVE"

docker image inspect "crm-backend:`$CRM_VERSION" >/dev/null
docker image inspect "crm-mcp:`$CRM_VERSION" >/dev/null
docker image inspect "crm-frontend:`$CRM_VERSION" >/dev/null

echo "应用镜像加载完成：`$CRM_VERSION"
"@

    $RestartContent = @"
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="`$(cd "`$(dirname "`$0")" && pwd)"
CRM_VERSION="`${1:-$Version}"
DEPLOY_DIR="`${2:-`${CRM_DEPLOY_DIR:-}}"

compose() {
  if docker compose version >/dev/null 2>&1; then
    docker compose "`$@"
    return
  fi
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "`$@"
    return
  fi
  echo "未找到Docker Compose"
  exit 1
}

resolve_deploy_dir() {
  if [ -n "`$DEPLOY_DIR" ]; then
    echo "`$DEPLOY_DIR"
    return
  fi

  if [ -f "`$SCRIPT_DIR/docker-compose.yml" ] && [ -f "`$SCRIPT_DIR/.env" ]; then
    echo "`$SCRIPT_DIR"
    return
  fi

  if [ -f "/app/builds/products/crm/current/docker-compose.yml" ]; then
    echo "/app/builds/products/crm/current"
    return
  fi

  local latest_dir
  latest_dir="`$(find /app/builds/products/crm -maxdepth 2 -name docker-compose.yml -printf '%T@ %h\n' 2>/dev/null | sort -nr | head -n 1 | cut -d' ' -f2-)"
  if [ -n "`$latest_dir" ]; then
    echo "`$latest_dir"
    return
  fi

  echo ""
}

DEPLOY_DIR="`$(resolve_deploy_dir)"
if [ -z "`$DEPLOY_DIR" ] || [ ! -f "`$DEPLOY_DIR/docker-compose.yml" ]; then
  echo "未找到部署目录，请通过第二个参数或CRM_DEPLOY_DIR指定包含docker-compose.yml的目录"
  exit 1
fi

cd "`$DEPLOY_DIR"

SOURCE_COMPOSE="`$SCRIPT_DIR/docker-compose.yml"
TARGET_COMPOSE="`$DEPLOY_DIR/docker-compose.yml"
if [ -f "`$SOURCE_COMPOSE" ] && [ "`$SOURCE_COMPOSE" != "`$TARGET_COMPOSE" ]; then
  cp "`$SOURCE_COMPOSE" "`$TARGET_COMPOSE"
  echo "已同步docker-compose.yml"
fi

if [ ! -f ".env" ]; then
  echo "部署目录缺少.env：`$DEPLOY_DIR/.env"
  exit 1
fi

TMP_ENV=".env.tmp.`$$"
if grep -q '^CRM_VERSION=' .env; then
  sed "s/^CRM_VERSION=.*/CRM_VERSION=`$CRM_VERSION/" .env > "`$TMP_ENV"
else
  cp .env "`$TMP_ENV"
  printf '\nCRM_VERSION=%s\n' "`$CRM_VERSION" >> "`$TMP_ENV"
fi
mv "`$TMP_ENV" .env

echo "部署目录：`$DEPLOY_DIR"
echo "应用版本：`$CRM_VERSION"
echo "重启后端、MCP服务和前端"
compose up -d --no-deps --force-recreate --no-build --pull never crm-backend crm-mcp crm-frontend

echo "当前应用容器状态"
compose ps crm-backend crm-mcp crm-frontend
"@

    $DeployContent = @"
#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="`$(cd "`$(dirname "`$0")" && pwd)"
CRM_VERSION="`${1:-$Version}"
DEPLOY_DIR="`${2:-`${CRM_DEPLOY_DIR:-}}"

bash "`$SCRIPT_DIR/load-app-images.sh" "`$CRM_VERSION"

if [ -n "`$DEPLOY_DIR" ]; then
  bash "`$SCRIPT_DIR/restart-crm-app.sh" "`$CRM_VERSION" "`$DEPLOY_DIR"
else
  bash "`$SCRIPT_DIR/restart-crm-app.sh" "`$CRM_VERSION"
fi
"@

    $VersionContent = @"
CRM_VERSION=$Version
BUILD_TIME=$(Get-Date -Format "yyyy-MM-dd HH:mm:ss")
BACKEND_IMAGE=crm-backend:$Version
MCP_IMAGE=crm-mcp:$Version
FRONTEND_IMAGE=crm-frontend:$Version
"@

    Write-Utf8NoBomFile -Path $LoadScript -Content $LoadContent
    Write-Utf8NoBomFile -Path $RestartScript -Content $RestartContent
    Write-Utf8NoBomFile -Path $DeployScript -Content $DeployContent
    Write-Utf8NoBomFile -Path $VersionFile -Content $VersionContent

    return @($LoadScript, $RestartScript, $DeployScript, $VersionFile)
}

function Invoke-RemoteCommand {
    param(
        [string]$CommandText,
        [string]$Description,
        [string]$RemoteHost,
        [string]$RemoteUser,
        [string]$RemotePassword
    )

    $RemoteTarget = "$RemoteUser@$RemoteHost"
    if (-not [string]::IsNullOrWhiteSpace($RemotePassword) -and (Get-Command "plink" -ErrorAction SilentlyContinue)) {
        Invoke-CheckedCommand `
            -Command "plink" `
            -Arguments @("-batch", "-pw", $RemotePassword, $RemoteTarget, $CommandText) `
            -Description $Description
        return
    }

    Assert-CommandExists -Command "ssh" -Description "OpenSSH ssh或PuTTY plink"
    Invoke-CheckedCommand `
        -Command "ssh" `
        -Arguments @($RemoteTarget, $CommandText) `
        -Description $Description
}

function Invoke-RemoteUpload {
    param(
        [string[]]$Files,
        [string]$RemoteHost,
        [string]$RemoteUser,
        [string]$RemotePath,
        [string]$RemotePassword
    )

    $RemoteTarget = "$RemoteUser@$RemoteHost"
    $RemoteDestination = "${RemoteTarget}:$RemotePath/"
    if (-not [string]::IsNullOrWhiteSpace($RemotePassword) -and (Get-Command "pscp" -ErrorAction SilentlyContinue)) {
        Invoke-CheckedCommand `
            -Command "pscp" `
            -Arguments (@("-batch", "-pw", $RemotePassword) + $Files + @($RemoteDestination)) `
            -Description "上传应用镜像和部署脚本"
        return
    }

    if (-not [string]::IsNullOrWhiteSpace($RemotePassword)) {
        Write-Host "检测到远程密码，但未找到pscp；将使用scp并由系统提示输入密码。" -ForegroundColor Yellow
    }

    Assert-CommandExists -Command "scp" -Description "OpenSSH scp或PuTTY pscp"
    Invoke-CheckedCommand `
        -Command "scp" `
        -Arguments ($Files + @($RemoteDestination)) `
        -Description "上传应用镜像和部署脚本"
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
if ([string]::IsNullOrWhiteSpace($RemotePath) -or -not $RemotePath.StartsWith("/")) {
    throw "远程上传目录必须是Linux绝对路径"
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
$McpContextDir = Join-Path $ContextDir "mcp"
$FrontendContextDir = Join-Path $ContextDir "frontend"
$FrontendBuildDir = Join-Path $ContextDir "frontend-build"
$BackendImage = "crm-backend:$Version"
$McpImage = "crm-mcp:$Version"
$FrontendImage = "crm-frontend:$Version"
$BackendArchive = Join-Path $OutputDir "crm-backend-$Version.tar"
$McpArchive = Join-Path $OutputDir "crm-mcp-$Version.tar"
$FrontendArchive = Join-Path $OutputDir "crm-frontend-$Version.tar"
$ComposeFile = Join-Path $OutputDir "docker-compose.yml"
$ShouldUpload = $Upload.IsPresent -or $DeployRemote.IsPresent
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
New-Item -ItemType Directory -Force -Path $McpContextDir | Out-Null
New-Item -ItemType Directory -Force -Path $FrontendContextDir | Out-Null
New-Item -ItemType Directory -Force -Path $FrontendBuildDir | Out-Null

try {
    Set-BuildJavaHome -RequestedJavaHome $JavaHome -OriginalPath $OriginalPath

    Push-Location $BackendDir
    try {
        Invoke-CheckedCommand `
            -Command "mvn" `
            -Arguments @("-DskipTests", "-pl", "crm-web,crm-mcp", "-am", "clean", "package") `
            -Description "构建后端和MCP服务Jar"
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

    $McpJar = Get-ChildItem `
        -LiteralPath (Join-Path $BackendDir "crm-mcp\target") `
        -Filter "crm-mcp-*.jar" |
        Where-Object { $_.Name -notlike "*.original" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $McpJar) {
        throw "未找到MCP服务Jar：backend\crm-mcp\target\crm-mcp-*.jar"
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

    Copy-Item -LiteralPath $McpJar.FullName -Destination (Join-Path $McpContextDir "app.jar")
    Copy-Item `
        -LiteralPath (Join-Path $DockerDir "mcp\Dockerfile") `
        -Destination (Join-Path $McpContextDir "Dockerfile")

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
        -Arguments @("build", "--pull=false", "-t", $McpImage, $McpContextDir) `
        -Description "构建MCP服务Docker镜像"

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
        -Arguments @("save", "-o", $McpArchive, $McpImage) `
        -Description "导出MCP服务镜像"

    Invoke-CheckedCommand `
        -Command "docker" `
        -Arguments @("save", "-o", $FrontendArchive, $FrontendImage) `
        -Description "导出前端镜像"

    $BackendHash = Get-FileHash -LiteralPath $BackendArchive -Algorithm SHA256
    $McpHash = Get-FileHash -LiteralPath $McpArchive -Algorithm SHA256
    $FrontendHash = Get-FileHash -LiteralPath $FrontendArchive -Algorithm SHA256
    $ChecksumFile = Join-Path $OutputDir "SHA256SUMS.txt"
    @(
        "$($BackendHash.Hash.ToLower())  $($BackendHash.Path | Split-Path -Leaf)"
        "$($McpHash.Hash.ToLower())  $($McpHash.Path | Split-Path -Leaf)"
        "$($FrontendHash.Hash.ToLower())  $($FrontendHash.Path | Split-Path -Leaf)"
    ) | Set-Content -LiteralPath $ChecksumFile -Encoding ASCII
    Copy-Item -LiteralPath (Join-Path $DeployDir "docker-compose.yml") -Destination $ComposeFile
    $ServerScripts = New-ServerScripts -OutputDir $OutputDir -Version $Version

    if ($ShouldUpload) {
        Invoke-RemoteCommand `
            -CommandText "mkdir -p '$RemotePath'" `
            -Description "创建远程上传目录" `
            -RemoteHost $RemoteHost `
            -RemoteUser $RemoteUser `
            -RemotePassword $RemotePassword

        $UploadFiles = @($BackendArchive, $McpArchive, $FrontendArchive, $ChecksumFile, $ComposeFile) + $ServerScripts
        Invoke-RemoteUpload `
            -Files $UploadFiles `
            -RemoteHost $RemoteHost `
            -RemoteUser $RemoteUser `
            -RemotePath $RemotePath `
            -RemotePassword $RemotePassword

        Invoke-RemoteCommand `
            -CommandText "chmod +x '$RemotePath/load-app-images.sh' '$RemotePath/restart-crm-app.sh' '$RemotePath/deploy-crm-app.sh'" `
            -Description "授权远程部署脚本" `
            -RemoteHost $RemoteHost `
            -RemoteUser $RemoteUser `
            -RemotePassword $RemotePassword
    }

    if ($DeployRemote.IsPresent) {
        $RemoteDeployCommand = "cd '$RemotePath' && bash deploy-crm-app.sh '$Version'"
        if (-not [string]::IsNullOrWhiteSpace($RemoteDeployDir)) {
            $RemoteDeployCommand = "cd '$RemotePath' && bash deploy-crm-app.sh '$Version' '$RemoteDeployDir'"
        }
        Invoke-RemoteCommand `
            -CommandText $RemoteDeployCommand `
            -Description "远程加载镜像并重启应用" `
            -RemoteHost $RemoteHost `
            -RemoteUser $RemoteUser `
            -RemotePassword $RemotePassword
    }

    Write-Host ""
    Write-Host "构建完成" -ForegroundColor Green
    Write-Host "后端镜像：$BackendImage"
    Write-Host "MCP服务镜像：$McpImage"
    Write-Host "前端镜像：$FrontendImage"
    Write-Host "输出目录：$OutputDir"
    Write-Host ""
    Write-Host "上传以下文件到服务器：" -ForegroundColor Yellow
    Write-Host "  $BackendArchive"
    Write-Host "  $McpArchive"
    Write-Host "  $FrontendArchive"
    Write-Host "  $ChecksumFile"
    Write-Host "  $ComposeFile"
    foreach ($ServerScript in $ServerScripts) {
        Write-Host "  $ServerScript"
    }
    Write-Host ""
    Write-Host "服务器执行：" -ForegroundColor Yellow
    Write-Host "  cd $RemotePath"
    Write-Host "  bash deploy-crm-app.sh $Version"
    Write-Host ""
    Write-Host "注意：Version必须与服务器.env中的CRM_VERSION一致。" -ForegroundColor Yellow
    if ($ShouldUpload) {
        Write-Host "远程目录：${RemoteUser}@${RemoteHost}:$RemotePath" -ForegroundColor Green
    }
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
