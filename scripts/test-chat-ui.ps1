[CmdletBinding()]
param([string] $Bundle, [string] $Serial)
& "$PSScriptRoot/verification/RunAndroidProbe.ps1" -Source "$PSScriptRoot/verification/ChatUiProbe.java" -SupportSource @("$PSScriptRoot/verification/UiFullscreenWindowProbe.java", "$PSScriptRoot/verification/SettingsWindowProbe.java") -Bundle $Bundle -Serial $Serial
