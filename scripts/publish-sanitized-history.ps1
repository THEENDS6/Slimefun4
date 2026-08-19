param(
    [switch]$Publish,
    [switch]$Check,
    [switch]$CheckHistory,
    [string]$Source = 'master',
    [string]$Remote = 'origin',
    [string]$Branch = 'master'
)

$launcher = Get-Command python -ErrorAction SilentlyContinue
$launcherArgs = @()
if (-not $launcher) {
    $launcher = Get-Command py.exe -ErrorAction SilentlyContinue
    $launcherArgs = @('-3')
}
if (-not $launcher) {
    throw 'Python 3 is required.'
}

$arguments = @((Join-Path $PSScriptRoot 'publish_sanitized_history.py'))
if ($Publish) { $arguments += '--publish' }
elseif ($CheckHistory) { $arguments += '--check-history' }
else { $arguments += '--check' }
$arguments += @('--source', $Source, '--remote', $Remote, '--branch', $Branch)
& $launcher.Source @launcherArgs @arguments
exit $LASTEXITCODE
