# Deterministic regression check for MovementFixModule's coordinate transform.
# This mirrors worldVectorFromInput/inputFromWorldVector without requiring a
# running Minecraft client, and covers all WASD combinations plus wrap-around.
$ErrorActionPreference = 'Stop'

function World-Vector([double]$forward, [double]$strafe, [double]$yaw) {
    [double]$r = $yaw * [Math]::PI / 180.0
    [double]$sinYaw = [Math]::Sin($r); [double]$cosYaw = [Math]::Cos($r)
    $result = New-Object double[] 2
    $result[0] = ([double]$strafe) * $cosYaw - ([double]$forward) * $sinYaw
    $result[1] = ([double]$forward) * $cosYaw + ([double]$strafe) * $sinYaw
    return ,$result
}

function Correct-Input([double]$forward, [double]$strafe, [double]$serverYaw, [double]$realYaw) {
    # The server interprets raw WASD at server yaw. Vanilla still consumes the
    # corrected input with the untouched real player yaw.
    $world = World-Vector $forward $strafe $serverYaw
    [double]$r = $realYaw * [Math]::PI / 180.0
    [double]$sinYaw = [Math]::Sin($r); [double]$cosYaw = [Math]::Cos($r)
    $result = New-Object double[] 2
    $result[0] = ([double]$world[1]) * $cosYaw - ([double]$world[0]) * $sinYaw
    $result[1] = ([double]$world[0]) * $cosYaw + ([double]$world[1]) * $sinYaw
    return ,$result
}

function Apply-Mode([string]$mode, [double]$forward, [double]$strafe, [double]$serverYaw, [double]$realYaw) {
    if ($mode -eq 'Silent' -or $mode -eq 'Strict') {
        return Correct-Input $forward $strafe $serverYaw $realYaw
    }
    # Ignore and Visual deliberately bypass the movement-input correction.
    $result = New-Object double[] 2
    $result[0] = $forward; $result[1] = $strafe
    return ,$result
}

$inputs = @('1,0','-1,0','0,1','0,-1','1,1','1,-1','-1,1','-1,-1')
$angles = @(0,45,90,135,180,-90,-135,359,1)
$epsilon = 0.00001
foreach ($real in $angles) {
    foreach ($effective in $angles) {
        foreach ($pair in $inputs) {
            $components = $pair.Split(',')
            $forward = [double]$components[0]
            $strafe = [double]$components[1]
            $expected = World-Vector $forward $strafe $effective
            $corrected = Apply-Mode 'Strict' $forward $strafe $effective $real
            $actual = World-Vector $corrected[0] $corrected[1] $real
            if ([Math]::Abs($expected[0] - $actual[0]) -gt $epsilon -or
                [Math]::Abs($expected[1] - $actual[1]) -gt $epsilon) {
                throw "movement invariant failed: real=$real server=$effective input=$pair"
            }
            foreach ($passthrough in @('Ignore', 'Visual')) {
                $untouched = Apply-Mode $passthrough $forward $strafe $effective $real
                if ($untouched[0] -ne $forward -or $untouched[1] -ne $strafe) {
                    throw "$passthrough must not transform movement input"
                }
            }
        }
    }
}
Write-Output 'MovementFix vector invariant passed for all tested angles and WASD combinations.'
