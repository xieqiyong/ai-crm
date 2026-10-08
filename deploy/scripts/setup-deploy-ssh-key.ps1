param(
    [string]$RemoteHost = "192.168.50.105",

    [string]$RemoteUser = "root",

    [string]$KeyPath = ""
)

$ErrorActionPreference = "Stop"

function Assert-CommandExists {
    param(
        [string]$Command,
        [string]$Description
    )

    if (-not (Get-Command $Command -ErrorAction SilentlyContinue)) {
        throw "未找到$Description，请先安装并加入环境变量：$Command"
    }
}

function Invoke-NativeCommand {
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

Assert-CommandExists -Command "ssh" -Description "OpenSSH ssh"
Assert-CommandExists -Command "ssh-keygen" -Description "OpenSSH ssh-keygen"

if ([string]::IsNullOrWhiteSpace($env:USERPROFILE)) {
    throw "无法识别当前Windows用户目录"
}

if ([string]::IsNullOrWhiteSpace($KeyPath)) {
    $KeyPath = Join-Path $env:USERPROFILE ".ssh\crm_deploy_ed25519"
}

$KeyDirectory = Split-Path -Parent $KeyPath
if (-not (Test-Path -LiteralPath $KeyDirectory -PathType Container)) {
    New-Item -ItemType Directory -Force -Path $KeyDirectory | Out-Null
}

if (-not (Test-Path -LiteralPath $KeyPath -PathType Leaf)) {
    Invoke-NativeCommand `
        -Command "ssh-keygen" `
        -Arguments @("-t", "ed25519", "-C", "crm-deploy", "-f", $KeyPath, "-N", "") `
        -Description "生成CRM部署专用SSH密钥"
}

$PublicKeyPath = "$KeyPath.pub"
if (-not (Test-Path -LiteralPath $PublicKeyPath -PathType Leaf)) {
    throw "SSH公钥文件不存在：$PublicKeyPath"
}

$ResolvedKeyPath = (Resolve-Path -LiteralPath $KeyPath).Path
$PublicKey = (Get-Content -LiteralPath $PublicKeyPath -Raw).Trim()
if ([string]::IsNullOrWhiteSpace($PublicKey)) {
    throw "SSH公钥内容为空：$PublicKeyPath"
}
if ($PublicKey.Contains("'")) {
    throw "SSH公钥注释中不能包含单引号，请重新生成部署专用密钥"
}

$RemoteTarget = "$RemoteUser@$RemoteHost"
$InstallCommand = "umask 077; mkdir -p ~/.ssh; touch ~/.ssh/authorized_keys; grep -qxF '$PublicKey' ~/.ssh/authorized_keys || printf '%s\n' '$PublicKey' >> ~/.ssh/authorized_keys; chmod 700 ~/.ssh; chmod 600 ~/.ssh/authorized_keys"

Write-Host ""
Write-Host "==> 安装部署公钥，下面只需要输入这一次服务器密码" -ForegroundColor Cyan
& ssh $RemoteTarget $InstallCommand
if ($LASTEXITCODE -ne 0) {
    throw "安装部署公钥失败，退出码：$LASTEXITCODE"
}

Invoke-NativeCommand `
    -Command "ssh" `
    -Arguments @(
        "-i",
        $ResolvedKeyPath,
        "-o",
        "BatchMode=yes",
        "-o",
        "IdentitiesOnly=yes",
        $RemoteTarget,
        "echo CRM_DEPLOY_SSH_OK"
    ) `
    -Description "验证SSH免密登录"

[Environment]::SetEnvironmentVariable("CRM_DEPLOY_SSH_KEY", $ResolvedKeyPath, "User")
$env:CRM_DEPLOY_SSH_KEY = $ResolvedKeyPath

Write-Host ""
Write-Host "SSH免密部署配置完成" -ForegroundColor Green
Write-Host "私钥位置：$ResolvedKeyPath"
Write-Host "后续可直接执行原有build-app-images.ps1命令，无需输入服务器密码。"
