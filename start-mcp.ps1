param([string]$OutputDirectory = (Join-Path $PSScriptRoot 'output'))
$ErrorActionPreference = 'Stop'
& uv run --project $PSScriptRoot --frozen python (Join-Path $PSScriptRoot 'mcp_server.py') --output $OutputDirectory
exit $LASTEXITCODE
