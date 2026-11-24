param([switch]$BuildImages)
Write-Host 'The legacy demo entry now starts platform dev.'
& (Join-Path $PSScriptRoot 'start-dev.ps1') -BuildImages:$BuildImages
