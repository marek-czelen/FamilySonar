<#
.SYNOPSIS
    Publikuje lokalnie skompilowany APK jako GitHub Release.

.DESCRIPTION
    Skrypt:
      1. (opcjonalnie) buduje release APK, jeśli go nie ma lub podano -Rebuild,
      2. tworzy tag git vX.Y i wypycha go na origin,
      3. tworzy Release na GitHubie i wgrywa findme.apk.

    Wymaga tokena GitHub (Personal Access Token z uprawnieniem "repo" lub
    "Contents: write") w zmiennej srodowiskowej GITHUB_TOKEN.

.EXAMPLE
    $env:GITHUB_TOKEN = "ghp_xxxxxxxx"
    .\release.ps1 -Version 1.1 -Notes "Poprawki i nowe funkcje"

.EXAMPLE
    .\release.ps1 -Version 1.2 -Rebuild
#>
param(
    [Parameter(Mandatory = $true)]
    [string]$Version,

    [string]$Notes = "",

    [switch]$Rebuild
)

$ErrorActionPreference = "Stop"

function Fail($msg) {
    Write-Host "BLAD: $msg" -ForegroundColor Red
    exit 1
}

# --- 1. Token ---------------------------------------------------------------
$token = $env:GITHUB_TOKEN
if ([string]::IsNullOrWhiteSpace($token)) {
    Fail "Ustaw token: `$env:GITHUB_TOKEN = 'ghp_...' (PAT z uprawnieniem 'repo' / 'Contents: write')."
}

# --- 2. Owner/repo z origin -------------------------------------------------
$remote = (git remote get-url origin).Trim()
if ($remote -match "github\.com[:/](.+?)/(.+?)(?:\.git)?/?$") {
    $owner = $Matches[1]
    $repo  = $Matches[2]
} else {
    Fail "Nie rozpoznano zdalnego repo GitHub z origin: $remote"
}
$tag = "v$Version"
Write-Host "Repo: $owner/$repo   Tag: $tag" -ForegroundColor Cyan

# --- 3. Tag nie moze juz istniec -------------------------------------------
if (git tag --list $tag) {
    Fail "Tag $tag juz istnieje lokalnie. Uzyj innej wersji lub usun tag."
}

# --- 4. APK (buduj gdy trzeba) ---------------------------------------------
$apk = "app\build\outputs\apk\release\findme.apk"
if ($Rebuild -or -not (Test-Path $apk)) {
    if (-not $env:JAVA_HOME) {
        $jbr = "C:\Program Files\Android\Android Studio\jbr"
        if (Test-Path $jbr) { $env:JAVA_HOME = $jbr }
    }
    Write-Host "Buduje release APK..." -ForegroundColor Cyan
    & .\gradlew.bat assembleRelease --console=plain
    if ($LASTEXITCODE -ne 0) { Fail "Build sie nie powiodl." }
} else {
    Write-Host "Uzywam istniejacego APK: $apk" -ForegroundColor Yellow
}
if (-not (Test-Path $apk)) { Fail "Nie znaleziono APK: $apk" }

# --- 5. Tag + push ----------------------------------------------------------
Write-Host "Tworze i wypycham tag $tag..." -ForegroundColor Cyan
git tag -a $tag -m "$repo $Version"
if ($LASTEXITCODE -ne 0) { Fail "Nie udalo sie utworzyc tagu." }
git push origin $tag
if ($LASTEXITCODE -ne 0) {
    git tag -d $tag | Out-Null
    Fail "Nie udalo sie wypchnac tagu (cofnieto lokalny tag)."
}

# --- 6. Utworz Release ------------------------------------------------------
$headers = @{
    Authorization = "Bearer $token"
    "User-Agent"  = "findme-release-script"
    Accept        = "application/vnd.github+json"
}
$bodyJson = @{
    tag_name   = $tag
    name       = "$repo $Version"
    body       = $Notes
    draft      = $false
    prerelease = $false
} | ConvertTo-Json

Write-Host "Tworze Release na GitHubie..." -ForegroundColor Cyan
try {
    $release = Invoke-RestMethod -Method Post `
        -Uri "https://api.github.com/repos/$owner/$repo/releases" `
        -Headers $headers -Body $bodyJson -ContentType "application/json"
} catch {
    Fail "Nie udalo sie utworzyc Release: $($_.Exception.Message)"
}

# --- 7. Wgraj APK -----------------------------------------------------------
$uploadUri = "https://uploads.github.com/repos/$owner/$repo/releases/$($release.id)/assets?name=findme.apk"
Write-Host "Wgrywam findme.apk..." -ForegroundColor Cyan
try {
    $asset = Invoke-RestMethod -Method Post -Uri $uploadUri `
        -Headers $headers -InFile $apk `
        -ContentType "application/vnd.android.package-archive"
} catch {
    Fail "Nie udalo sie wgrac APK: $($_.Exception.Message)"
}

Write-Host ""
Write-Host "GOTOWE!" -ForegroundColor Green
Write-Host "Release: $($release.html_url)"
Write-Host "APK:     $($asset.browser_download_url)"
