<#
.SYNOPSIS
    Syncs internal documentation from FreezeYou to the private freezeyou-docs repository and pushes to GitHub.
#>

$sourceDir = "c:\Users\Vu\git_projects\FreezeYou"
$targetDir = "c:\Users\Vu\git_projects\freezeyou-docs"

$files = @(
    "CLAUDE.md",
    "ROADMAP.md",
    "ROADMAP.en.md",
    "ARCHITECTURE.md",
    "LESSONS_LEARNED.md",
    "HISTORY_ARCHIVE.md",
    "LOCALE_TITLES_CHECK.md",
    "freezeyou_zh-CN_combined.md"
)

if (-not (Test-Path $targetDir)) {
    Write-Error "Target directory $targetDir does not exist."
    exit 1
}

foreach ($file in $files) {
    $src = Join-Path $sourceDir $file
    if (Test-Path $src) {
        Copy-Item -Path $src -Destination $targetDir -Force
        Write-Host "Copied $file"
    }
}

$auditSource = Join-Path $sourceDir ".agents\audit"
$auditTarget = Join-Path $targetDir "audit"
if (Test-Path $auditSource) {
    New-Item -ItemType Directory -Force -Path $auditTarget | Out-Null
    Copy-Item -Path (Join-Path $auditSource "*") -Destination $auditTarget -Recurse -Force
    Write-Host "Copied .agents/audit"
}

Push-Location $targetDir
try {
    $status = git status --porcelain
    if ($status) {
        git add .
        $dateStr = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
        git commit -m "docs: update internal documentation ($dateStr)"
        git push origin main
        Write-Host "Successfully synced and pushed documentation to private repository." -ForegroundColor Green
    } else {
        Write-Host "No changes in documentation to commit." -ForegroundColor Cyan
    }
} finally {
    Pop-Location
}
