<#
.SYNOPSIS
    把「风车」的聊天记录 + 记忆导入到安卓设备（换电脑 / 换手机继续用）。

.DESCRIPTION
    读取一份 lhagnet-data-*.json（或 App 导出的 lhagnet-backup-*.json），
    拆成各组件自己的 JSON 文件，再通过 adb push 回设备私有目录。

    同样需要 root 或 debuggable 应用才能写 /data/data。

.PARAMETER File
    要导入的 JSON 文件。省略时自动取本目录最新的 lhagnet-data-*.json。

.PARAMETER Serial
    指定设备序列号。省略时自动使用唯一连接的设备。

.PARAMETER Merge
    追加到设备现有数据之后（默认）。加 -Replace 则先清空再导入。

.EXAMPLE
    .\Import-LhagnetData.ps1
    .\Import-LhagnetData.ps1 -File .\lhagnet-data-20261027-101500.json
#>
[CmdletBinding()]
param(
    [string]$File,
    [string]$Serial,
    [string]$Adb,
    [switch]$Replace
)

$ErrorActionPreference = 'Stop'

function Find-Adb {
    if ($Adb -and (Test-Path $Adb)) { return $Adb }
    $c = Get-Command adb -ErrorAction SilentlyContinue
    if ($c) { return $c.Source }
    $candidates = @(
        "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
        "$env:ANDROID_HOME\platform-tools\adb.exe",
        "$env:ANDROID_SDK_ROOT\platform-tools\adb.exe",
        "D:\A1\build-tools\android-sdk\platform-tools\adb.exe",
        "C:\Program Files (x86)\TC综合开发工具\android\adb.exe"
    )
    foreach ($p in $candidates) { if ($p -and (Test-Path $p)) { return $p } }
    return $null
}

$adb = Find-Adb
if (-not $adb) {
    Write-Host "[X] 找不到 adb.exe" -ForegroundColor Red; exit 1
}

# ---------- 找文件 ----------
if (-not $File) {
    $cand = Get-ChildItem $PSScriptRoot -Filter 'lhagnet-data-*.json' -File -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending
    if (-not $cand) {
        Write-Host "[X] 本目录没有 lhagnet-data-*.json，请用 -File 指定" -ForegroundColor Red
        exit 1
    }
    $File = $cand[0].FullName
}
if (-not (Test-Path $File)) {
    Write-Host "[X] 文件不存在: $File" -ForegroundColor Red; exit 1
}
Write-Host "[i] 导入文件: $File"

$data = Get-Content $File -Raw -Encoding UTF8 | ConvertFrom-Json
if ($null -eq $data) { Write-Host "[X] JSON 解析失败" -ForegroundColor Red; exit 1 }

# ---------- 找设备 ----------
$devOut = & $adb devices 2>&1
$devices = @()
foreach ($line in ($devOut | Select-Object -Skip 1)) {
    if ($line -match '^(\S+)\s+device$') { $devices += $matches[1] }
}
if ($devices.Count -eq 0) {
    Write-Host "[X] 没有检测到已连接的设备" -ForegroundColor Red; exit 1
}
if ($Serial) {
    if ($devices -notcontains $Serial) {
        Write-Host "[X] 设备 '$Serial' 不在列表中。已连接：$($devices -join ', ')" -ForegroundColor Red; exit 1
    }
} else {
    if ($devices.Count -gt 1) {
        Write-Host "[X] 检测到多台设备：$($devices -join ', ')，请用 -Serial 指定" -ForegroundColor Red; exit 1
    }
    $Serial = $devices[0]
}
Write-Host "[i] 设备: $Serial"

$PKG = 'io.lhagnet.app'
$REMOTE = "/data/data/$PKG"

$work = Join-Path $env:TEMP ("lhagnet_import_" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $work | Out-Null

# ---------- 拆分成各组件文件 ----------
$plan = @()

# 记忆
$hasMemory = ($data.PSObject.Properties.Name -contains 'tasks') -or ($data.PSObject.Properties.Name -contains 'sites')
if ($hasMemory) {
    $memObj = [ordered]@{
        tasks = @($data.tasks)
        sites = @($data.sites)
    }
    $p = Join-Path $work 'memory.json'
    ($memObj | ConvertTo-Json -Depth 10) | Set-Content -Path $p -Encoding UTF8
    $plan += @{ Local = $p; Remote = "$REMOTE/files/memory/memory.json"; Label = "记忆 ($(@($data.tasks).Count) 任务 / $(@($data.sites).Count) 经验)" }
}

# 聊天记录
if ($data.PSObject.Properties.Name -contains 'chat' -and $null -ne $data.chat) {
    $p = Join-Path $work 'chat.json'
    (@($data.chat) | ConvertTo-Json -Depth 10) | Set-Content -Path $p -Encoding UTF8
    $plan += @{ Local = $p; Remote = "$REMOTE/files/chat/chat.json"; Label = "聊天记录 ($(@($data.chat).Count) 条)" }
}

# 技能
if ($data.PSObject.Properties.Name -contains 'skills' -and $null -ne $data.skills) {
    $sk = @($data.skills)
    $p = Join-Path $work 'skills.json'
    ($sk | ConvertTo-Json -Depth 10) | Set-Content -Path $p -Encoding UTF8
    $plan += @{ Local = $p; Remote = "$REMOTE/files/skills/skills.json"; Label = "技能 ($($sk.Count) 个)" }
}

if ($plan.Count -eq 0) {
    Write-Host "[X] 这个文件里没有可导入的内容" -ForegroundColor Red
    Write-Host "    期望包含 tasks / sites / chat / skills 字段之一" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "计划导入：" -ForegroundColor Cyan
foreach ($x in $plan) { Write-Host "   - $($x.Label)" }
Write-Host ""

# ---------- 先备份设备上的现有数据 ----------
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
foreach ($x in $plan) {
    $bak = "$($x.Remote).bak-$stamp"
    & $adb -s $Serial shell "cp '$($x.Remote)' '$bak' 2>/dev/null" 2>&1 | Out-Null
}
Write-Host "[i] 已尝试备份设备上原文件（*.bak-$stamp）" -ForegroundColor DarkGray

# ---------- push ----------
$okCount = 0
foreach ($x in $plan) {
    # 确保目标目录存在
    $dir = Split-Path $x.Remote -Parent
    & $adb -s $Serial shell "mkdir -p '$dir'" 2>&1 | Out-Null

    $out = & $adb -s $Serial push $x.Local $x.Remote 2>&1
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[OK] $($x.Label)  -> $($x.Remote)" -ForegroundColor Green
        $okCount++
    } else {
        Write-Host "[X] 失败: $($x.Label)" -ForegroundColor Red
        Write-Host "    $($out -join ' ')" -ForegroundColor DarkRed
    }
}

# ---------- 重启 App 让它重新读取 ----------
if ($okCount -gt 0) {
    & $adb -s $Serial shell "am force-stop $PKG" 2>&1 | Out-Null
    Write-Host ""
    Write-Host "[i] 已停止 App，重新打开即可看到导入的数据。" -ForegroundColor Cyan
}

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
if ($okCount -eq $plan.Count) {
    Write-Host "[OK] 全部导入成功（$okCount/$($plan.Count)）" -ForegroundColor Green
} else {
    Write-Host "[!] 部分成功（$okCount/$($plan.Count)）" -ForegroundColor Yellow
    Write-Host "    失败通常是 /data/data 需要 root 权限。" -ForegroundColor Yellow
    Write-Host "    替代方案：App 内 记忆 -> 导入备份 -> 粘贴 JSON 内容" -ForegroundColor Yellow
}
