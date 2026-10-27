<#
.SYNOPSIS
    从安卓设备导出「风车」的聊天记录 + 记忆（+ 技能），打包到本目录，方便换电脑继续用。

.DESCRIPTION
    通过 adb 从已连接的设备拉取 io.lhagnet.app 的私有数据：
      files/chat/chat.json      聊天记录
      files/memory/memory.json  记忆（任务履历 + 站点经验）
      files/skills/skills.json  技能
    并合并成一份 lhagnet-data-<时间戳>.json（可直接被 App 的「导入备份」读取）。

    注意：/data/data/... 需要 root 或 debuggable 应用才能读。
    - 已 root 的手机：直接运行
    - 未 root：需要 App 是 debug 构建（debuggable），否则会提示 Permission denied

.PARAMETER Serial
    指定设备序列号。省略时自动使用唯一连接的设备。

.EXAMPLE
    .\Export-LhagnetData.ps1
    .\Export-LhagnetData.ps1 -Serial R5CT30XXXXX
#>
[CmdletBinding()]
param(
    [string]$Serial,
    [string]$OutDir = $PSScriptRoot,
    [string]$Adb
)

$ErrorActionPreference = 'Stop'

# ---------- 定位 adb ----------
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
    Write-Host "[X] 找不到 adb.exe" -ForegroundColor Red
    Write-Host "    请安装 Android Platform Tools，或用 -Adb 参数指定路径。" -ForegroundColor Yellow
    exit 1
}
Write-Host "[i] adb: $adb"

# ---------- 找设备 ----------
$devOut = & $adb devices 2>&1
$devices = @()
foreach ($line in ($devOut | Select-Object -Skip 1)) {
    if ($line -match '^(\S+)\s+device$') { $devices += $matches[1] }
}
if ($devices.Count -eq 0) {
    Write-Host "[X] 没有检测到已连接的设备" -ForegroundColor Red
    Write-Host "    请用 USB 连接手机并开启「USB 调试」，或在手机上确认授权。" -ForegroundColor Yellow
    exit 1
}
if ($Serial) {
    if ($devices -notcontains $Serial) {
        Write-Host "[X] 设备 '$Serial' 不在列表中。已连接：$($devices -join ', ')" -ForegroundColor Red
        exit 1
    }
} else {
    if ($devices.Count -gt 1) {
        Write-Host "[X] 检测到多台设备：$($devices -join ', ')" -ForegroundColor Red
        Write-Host "    请用 -Serial 指定。" -ForegroundColor Yellow
        exit 1
    }
    $Serial = $devices[0]
}
Write-Host "[i] 设备: $Serial"

$PKG = 'io.lhagnet.app'
$REMOTE = "/data/data/$PKG"

# ---------- 测试读取权限 ----------
function Test-Readable($path) {
    $r = & $adb -s $Serial shell "ls -l $path 2>/dev/null" 2>&1
    return ($LASTEXITCODE -eq 0 -and ($r -match 'json'))
}

# ---------- 逐个拉取 ----------
$targets = @(
    @{ Name = 'chat.json';   Path = "$REMOTE/files/chat/chat.json";   Key = 'chat' },
    @{ Name = 'memory.json'; Path = "$REMOTE/files/memory/memory.json"; Key = 'memory' },
    @{ Name = 'skills.json'; Path = "$REMOTE/files/skills/skills.json"; Key = 'skills' }
)

$work = Join-Path $env:TEMP ("lhagnet_export_" + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Force -Path $work | Out-Null
$pulled = @{}

foreach ($t in $targets) {
    Write-Host "[>] 拉取 $($t.Name) ..."
    if (-not (Test-Readable $t.Path)) {
        Write-Host "    [跳过] 读不到 $($t.Path)（不存在 / 无权限）" -ForegroundColor DarkYellow
        continue
    }
    & $adb -s $Serial pull $t.Path (Join-Path $work $t.Name) 2>&1 | Out-Null
    $local = Join-Path $work $t.Name
    if (Test-Path $local) {
        $len = (Get-Item $local).Length
        if ($len -gt 0) {
            $pulled[$t.Key] = $local
            Write-Host "    [OK] $len 字节" -ForegroundColor Green
        } else {
            Write-Host "    [跳过] 文件为空" -ForegroundColor DarkYellow
        }
    }
}

if ($pulled.Count -eq 0) {
    Write-Host ""
    Write-Host "[X] 一个都没拉到。原因通常是：/data/data 需要 root 权限。" -ForegroundColor Red
    Write-Host ""
    Write-Host "解决办法（二选一）：" -ForegroundColor Yellow
    Write-Host "  1) 手机 root 后重跑本脚本" -ForegroundColor Yellow
    Write-Host "  2) 在 App 内操作：记忆 -> 导出备份 -> 文件会存到" -ForegroundColor Yellow
    Write-Host "     /sdcard/Android/data/io.lhagnet.app/files/lhagnet-backup-*.json" -ForegroundColor Yellow
    Write-Host "     然后把那个文件拷到电脑的 D:\A5\Lhagnet\data\ 目录即可" -ForegroundColor Yellow
    Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue
    exit 2
}

# ---------- 合并成一份 App 可直接导入的 JSON ----------
$combined = [ordered]@{
    app        = 'lhagnet'
    version    = 2
    exportedAt = [DateTimeOffset]::Now.ToUnixTimeMilliseconds()
    source     = "adb-export:$Serial"
}

function Convert-JsonSafe($file) {
    try { return (Get-Content $file -Raw -Encoding UTF8 | ConvertFrom-Json) } catch { return $null }
}

# 记忆：合并 tasks / sites
if ($pulled.ContainsKey('memory')) {
    $mem = Convert-JsonSafe $pulled['memory']
    if ($mem) {
        $combined['tasks'] = @($mem.tasks)
        $combined['sites'] = @($mem.sites)
        Write-Host "[i] 记忆: $(@($mem.tasks).Count) 条任务履历, $(@($mem.sites).Count) 条站点经验" -ForegroundColor Cyan
    }
}
# 聊天记录：chat.json 是数组
if ($pulled.ContainsKey('chat')) {
    $chat = Convert-JsonSafe $pulled['chat']
    if ($chat) {
        $combined['chat'] = @($chat)
        Write-Host "[i] 聊天记录: $(@($chat).Count) 条" -ForegroundColor Cyan
    }
}
# 技能：skills.json 结构与 memory 类似
if ($pulled.ContainsKey('skills')) {
    $sk = Convert-JsonSafe $pulled['skills']
    if ($sk) {
        if ($sk -is [array]) {
            $combined['skills'] = @($sk)
        } elseif ($sk.PSObject.Properties.Name -contains 'skills' -and $null -ne $sk.skills) {
            $combined['skills'] = @($sk.skills)
        } elseif ($sk.PSObject.Properties.Name -contains 'skills') {
            $combined['skills'] = @()
        } else {
            $combined['skills'] = @($sk)
        }
        Write-Host "[i] 技能: $(@($combined['skills']).Count) 个" -ForegroundColor Cyan
    }
}

New-Item -ItemType Directory -Force -Path $OutDir | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outFile = Join-Path $OutDir "lhagnet-data-$stamp.json"
($combined | ConvertTo-Json -Depth 10) | Set-Content -Path $outFile -Encoding UTF8

# 原始文件也留一份，方便手工查看
$rawDir = Join-Path $OutDir "raw-$stamp"
New-Item -ItemType Directory -Force -Path $rawDir | Out-Null
foreach ($k in $pulled.Keys) { Copy-Item $pulled[$k] (Join-Path $rawDir $k) -Force }

Remove-Item $work -Recurse -Force -ErrorAction SilentlyContinue

Write-Host ""
Write-Host "[OK] 导出完成" -ForegroundColor Green
Write-Host "    合并文件 : $outFile"
Write-Host "    原始文件 : $rawDir"
Write-Host ""
Write-Host "换到新电脑 / 新手机时：" -ForegroundColor Yellow
Write-Host "  1. 把 lhagnet-data-$stamp.json 拷过去" -ForegroundColor Yellow
Write-Host "  2. 风车 -> 记忆 -> 导入备份 -> 粘贴 JSON 内容" -ForegroundColor Yellow
