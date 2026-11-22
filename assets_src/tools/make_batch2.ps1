# Batch 2: 24 regular units + 9 bosses, 12 frames each, 0x72 source, 6-grey tint-ready.
# Run: powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\make_batch2.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SrcDir = Join-Path $Root 'assets_src\sourced\0x72-dungeontileset-ii'
$OutDir = Join-Path $Root 'assets_src\draft_batch2'
$UnitDir = Join-Path $OutDir 'units'
$PrevDir = Join-Path $OutDir 'previews'
$ColorDir = Join-Path $OutDir 'variants_color'
New-Item -ItemType Directory -Force -Path $UnitDir, $PrevDir, $ColorDir | Out-Null

$DARK = 40; $MID = 145; $LIGHT = 225; $WHITE = 255
$Pack = @{
    name = '0x72 DungeonTileset II'; author = '0x72 (Robert)'
    url = 'https://0x72.itch.io/dungeontileset-ii'
    license = 'CC0 1.0 (public domain)'
    attribution = 'No credit required (CC0). 0x72 (Robert), DungeonTileset II.'
}

# ---------------- image helpers ----------------
function Get-Frame {
    param([System.Drawing.Bitmap]$Sheet, [int]$Index, [int]$Fw, [int]$Fh)
    $bmp = [System.Drawing.Bitmap]::new($Fw, $Fh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($Sheet, [System.Drawing.Rectangle]::new(0, 0, $Fw, $Fh), [System.Drawing.Rectangle]::new($Index * $Fw, 0, $Fw, $Fh), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return $bmp
}

function Get-BBox {
    param([System.Drawing.Bitmap]$Bmp)
    $minX = 9999; $minY = 9999; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            if ($Bmp.GetPixel($x, $y).A -gt 0) {
                if ($x -lt $minX) { $minX = $x }; if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }; if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    return @{ x = $minX; y = $minY; w = ($maxX - $minX + 1); h = ($maxY - $minY + 1); right = $maxX }
}

function Get-RankLut {
    param([System.Drawing.Bitmap[]]$Frames)
    $lum = @{}
    foreach ($bmp in $Frames) {
        for ($y = 0; $y -lt $bmp.Height; $y++) {
            for ($x = 0; $x -lt $bmp.Width; $x++) {
                $c = $bmp.GetPixel($x, $y)
                if ($c.A -eq 0) { continue }
                $k = '{0},{1},{2}' -f $c.R, $c.G, $c.B
                if (-not $lum.ContainsKey($k)) { $lum[$k] = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }
            }
        }
    }
    $sorted = @($lum.GetEnumerator() | Sort-Object Value)
    $n = $sorted.Count
    $levels = @(31, 105, 140, 171, 205, 247)
    $lut = @{}
    for ($i = 0; $i -lt $n; $i++) {
        $t = if ($n -le 1) { 1.0 } else { $i / ($n - 1) }
        $idx = [int][math]::Round($t * 5)
        $lut[$sorted[$i].Key] = $levels[$idx]
    }
    return $lut
}

function Convert-ToGrey {
    param([System.Drawing.Bitmap]$Bmp, [hashtable]$Lut)
    $dst = [System.Drawing.Bitmap]::new($Bmp.Width, $Bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $v = $Lut['{0},{1},{2}' -f $c.R, $c.G, $c.B]
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $v, $v, $v))
        }
    }
    return $dst
}

function Resize-Nearest {
    param([System.Drawing.Bitmap]$Bmp, [int]$Dw, [int]$Dh)
    $dst = [System.Drawing.Bitmap]::new($Dw, $Dh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $Dh; $y++) {
        $sy = [int][math]::Floor($y * $Bmp.Height / $Dh)
        for ($x = 0; $x -lt $Dw; $x++) {
            $sx = [int][math]::Floor($x * $Bmp.Width / $Dw)
            $dst.SetPixel($x, $y, $Bmp.GetPixel($sx, $sy))
        }
    }
    return $dst
}

function Place-OnCanvas {
    param([System.Drawing.Bitmap]$Bmp, [int]$Baseline = 26)
    $canvas = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $x = [int][math]::Round((32 - $Bmp.Width) / 2.0)
    $y = $Baseline - $Bmp.Height + 1
    for ($sy = 0; $sy -lt $Bmp.Height; $sy++) {
        for ($sx = 0; $sx -lt $Bmp.Width; $sx++) {
            $c = $Bmp.GetPixel($sx, $sy)
            if ($c.A -eq 0) { continue }
            $tx = $x + $sx; $ty = $y + $sy
            if ($tx -ge 0 -and $tx -le 31 -and $ty -ge 0 -and $ty -le 31) { $canvas.SetPixel($tx, $ty, $c) }
        }
    }
    return $canvas
}

function Set-Px {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$V)
    if ($X -lt 0 -or $X -gt 31 -or $Y -lt 0 -or $Y -gt 31) { return }
    $Bmp.SetPixel($X, $Y, [System.Drawing.Color]::FromArgb(255, $V, $V, $V))
}

function Set-PxBehind {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$V)
    if ($X -lt 0 -or $X -gt 31 -or $Y -lt 0 -or $Y -gt 31) { return }
    if ($Bmp.GetPixel($X, $Y).A -ne 0) { return }
    $Bmp.SetPixel($X, $Y, [System.Drawing.Color]::FromArgb(255, $V, $V, $V))
}

function Draw-Rows {
    param([System.Drawing.Bitmap]$Bmp, [int]$X0, [int]$Y0, [array]$Rows, [int]$Value)
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $start = $Rows[$i][0]; $w = $Rows[$i][1]
        for ($j = 0; $j -lt $w; $j++) { Set-Px $Bmp ($X0 + $start + $j) ($Y0 + $i) $Value }
    }
}

function Draw-RowsOutline {
    param([System.Drawing.Bitmap]$Bmp, [int]$X0, [int]$Y0, [array]$Rows, [int]$Value, [int]$Grow = 1)
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $start = $Rows[$i][0] - $Grow; $w = $Rows[$i][1] + 2 * $Grow
        for ($j = 0; $j -lt $w; $j++) { Set-Px $Bmp ($X0 + $start + $j) ($Y0 + $i) $Value }
    }
}

function Draw-Line {
    param([System.Drawing.Bitmap]$Bmp, [int]$X0, [int]$Y0, [int]$X1, [int]$Y1, [int]$Value)
    $dx = [math]::Abs($X1 - $X0); $sx = if ($X0 -lt $X1) { 1 } else { -1 }
    $dy = -[math]::Abs($Y1 - $Y0); $sy = if ($Y0 -lt $Y1) { 1 } else { -1 }
    $err = $dx + $dy; $x = $X0; $y = $Y0
    while ($true) {
        Set-Px $Bmp $x $y $Value
        if ($x -eq $X1 -and $y -eq $Y1) { break }
        $e2 = 2 * $err
        if ($e2 -ge $dy) { $err += $dy; $x += $sx }
        if ($e2 -le $dx) { $err += $dx; $y += $sy }
    }
}

function Adjust-Value {
    param([System.Drawing.Bitmap]$Bmp, [double]$Factor)
    $dst = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $v = [int][math]::Min(255, [math]::Round($c.R * $Factor))
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $v, $v, $v))
        }
    }
    return $dst
}

function Add-Rim {
    param([System.Drawing.Bitmap]$Bmp)
    $copy = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 32; $x++) { $copy.SetPixel($x, $y, $Bmp.GetPixel($x, $y)) } }
    for ($y = 0; $y -le 26; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            if ($Bmp.GetPixel($x, $y).A -gt 0) { continue }
            foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
                $nx = $x + $d[0]; $ny = $y + $d[1]
                if ($nx -ge 0 -and $nx -lt 32 -and $ny -ge 0 -and $ny -lt 32 -and $Bmp.GetPixel($nx, $ny).A -gt 0) {
                    $copy.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, 255, 255, 255)); break
                }
            }
        }
    }
    return $copy
}

function Add-Lean {
    param([System.Drawing.Bitmap]$Bmp, [int]$Px)
    $dst = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -le 26; $y++) {
        $shift = [int][math]::Round($Px * (26 - $y) / 26.0)
        for ($x = 0; $x -lt 32; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $tx = $x + $shift
            if ($tx -ge 0 -and $tx -lt 32) { $dst.SetPixel($tx, $y, $c) }
        }
    }
    return $dst
}

function Add-Squash {
    param([System.Drawing.Bitmap]$Bmp, [double]$Factor)
    $dst = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -le 26; $y++) {
        $ty = 26 - [int][math]::Round((26 - $y) * $Factor)
        for ($x = 0; $x -lt 32; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -gt 0 -and $ty -ge 0) { $dst.SetPixel($x, $ty, $c) }
        }
    }
    return $dst
}

function Add-Rotate90 {
    param([System.Drawing.Bitmap]$Bmp)
    $bb = Get-BBox $Bmp
    $content = [System.Drawing.Bitmap]::new($bb.w, $bb.h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $bb.h; $y++) {
        for ($x = 0; $x -lt $bb.w; $x++) {
            $content.SetPixel($x, $y, $Bmp.GetPixel($bb.x + $x, $bb.y + $y))
        }
    }
    $content.RotateFlip([System.Drawing.RotateFlipType]::Rotate90FlipNone)
    $canvas = Place-OnCanvas $content 26
    $content.Dispose()
    return $canvas
}

function Get-BottomRow {
    param([System.Drawing.Bitmap]$Bmp)
    for ($y = 31; $y -ge 0; $y--) {
        for ($x = 0; $x -lt 32; $x++) { if ($Bmp.GetPixel($x, $y).A -gt 0) { return $y } }
    }
    return -1
}

function Get-ColorCount {
    param([System.Drawing.Bitmap]$Bmp)
    $set = @{}
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $set['{0},{1},{2}' -f $c.R, $c.G, $c.B] = 1
        }
    }
    return $set.Count
}

# ---------------- body cache ----------------
$script:BodyCache = @{}
function Get-BodyBase {
    param([string]$Body, [int]$Fw, [int]$Fh, [int]$TargetH, [string]$Type, [int]$Index)
    $key = '{0}|{1}|{2}|{3}' -f $Body, $TargetH, $Type, $Index
    if ($script:BodyCache.ContainsKey($key)) { return [System.Drawing.Bitmap]::new($script:BodyCache[$key]) }
    if (-not $script:BodyCache.ContainsKey("data|$Body")) {
        $idleSheet = [System.Drawing.Bitmap]::new((Join-Path $SrcDir ('monsters\' + $Body + '_idle_anim.png')))
        $runSheet = [System.Drawing.Bitmap]::new((Join-Path $SrcDir ('monsters\' + $Body + '_run_anim.png')))
        $idle = @(); $run = @()
        for ($i = 0; $i -lt [int]($idleSheet.Width / $Fw); $i++) { $idle += Get-Frame $idleSheet $i $Fw $Fh }
        for ($i = 0; $i -lt [int]($runSheet.Width / $Fw); $i++) { $run += Get-Frame $runSheet $i $Fw $Fh }
        $idleSheet.Dispose(); $runSheet.Dispose()
        $lut = Get-RankLut (@($idle) + @($run))
        $script:BodyCache["data|$Body"] = @{ idle = $idle; run = $run; lut = $lut }
    }
    $data = $script:BodyCache["data|$Body"]
    $frames = if ($Type -eq 'idle') { $data.idle } else { $data.run }
    $src = $frames[$Index]
    $bb = Get-BBox $src
    $crop = [System.Drawing.Bitmap]::new($bb.w, $bb.h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($crop)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.DrawImage($src, [System.Drawing.Rectangle]::new(0, 0, $bb.w, $bb.h), [System.Drawing.Rectangle]::new($bb.x, $bb.y, $bb.w, $bb.h), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $grey = Convert-ToGrey $crop $data.lut
    $scaled = Resize-Nearest $grey ([int][math]::Round($bb.w * $TargetH / $bb.h)) $TargetH
    $canvas = Place-OnCanvas $scaled 26
    $crop.Dispose(); $grey.Dispose(); $scaled.Dispose()
    $script:BodyCache[$key] = $canvas
    return [System.Drawing.Bitmap]::new($canvas)
}

# ---------------- race markers ----------------
function Add-RaceMarker {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Race)
    $cx = 16
    switch ($Race) {
        '兽人' { }
        '野兽' {
            $my = $BB.y + 6
            for ($dy = 0; $dy -le 2; $dy++) { for ($dx = -2; $dx -le 2; $dx++) { Set-Px $Bmp ($cx + $dx) ($my + $dy) $DARK } }
            for ($dy = 0; $dy -le 1; $dy++) { for ($dx = -1; $dx -le 1; $dx++) { Set-Px $Bmp ($cx + $dx) ($my + $dy) $LIGHT } }
            Set-Px $Bmp $cx ($my + 1) $DARK
        }
        '亡灵' {
            Draw-Line $Bmp ($cx - 3) ($BB.y + 8) ($cx + 3) ($BB.y + 8) $DARK
        }
        '巨人' {
            $py = $BB.y + 5
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $px = [math]::Max(0, $BB.x - 1) } else { $px = [math]::Min(27, $BB.right - 1) }
                for ($dy = 0; $dy -le 1; $dy++) { for ($dx = 0; $dx -le 3; $dx++) { Set-Px $Bmp ($px + $dx) ($py + $dy) $DARK } }
                Set-Px $Bmp ($px + 1) $py $LIGHT
                Set-Px $Bmp ($px + 2) $py $LIGHT
                Set-Px $Bmp ($px + 1) ($py + 1) $LIGHT
            }
            $by = $BB.y + 15
            for ($x = $cx - 3; $x -le $cx + 3; $x++) { Set-Px $Bmp $x $by $DARK; Set-Px $Bmp $x ($by + 1) $DARK }
            Set-Px $Bmp ($cx - 1) $by $WHITE; Set-Px $Bmp $cx $by $WHITE
            Set-Px $Bmp ($cx - 1) ($by + 1) $WHITE; Set-Px $Bmp $cx ($by + 1) $WHITE
        }
    }
}

# ---------------- class props ----------------
function Add-Shield {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    $sx = [math]::Max(0, $BB.x - 8); $sy = $BB.y + 6
    $shield = @(@(0, 7), @(0, 7), @(0, 7), @(0, 7), @(0, 7), @(1, 5), @(2, 3), @(3, 1))
    Draw-RowsOutline $Bmp $sx $sy $shield $DARK 1
    Draw-Rows $Bmp $sx $sy $shield $LIGHT
    Draw-Rows $Bmp ($sx + 2) ($sy + 3) @(@(0, 3), @(0, 3), @(0, 3)) $DARK
    Set-Px $Bmp ($sx + 3) ($sy + 4) $WHITE
}

function Add-Warrior {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Variant)
    Add-Shield $Bmp $BB
    $ax = [math]::Min(27, $BB.right + 3); $ay = $BB.y + 7
    for ($y = $ay + 1; $y -le 24; $y++) {
        Set-Px $Bmp ($ax - 1) $y $DARK; Set-Px $Bmp $ax $y $LIGHT; Set-Px $Bmp ($ax + 1) $y $DARK
    }
    switch ($Variant) {
        'axe' {
            $head = @(@(3, 2), @(2, 4), @(1, 6), @(1, 6), @(2, 4), @(3, 2))
            Draw-RowsOutline $Bmp ($ax - 2) ($ay - 2) $head $DARK 1
            Draw-Rows $Bmp ($ax - 2) ($ay - 2) $head $LIGHT
            Draw-Line $Bmp ($ax - 1) ($ay + 1) ($ax - 1) ($ay + 4) $DARK
            Set-Px $Bmp ($ax + 1) ($ay - 1) $WHITE
        }
        'hammer' {
            $head = @(@(2, 4), @(1, 6), @(1, 6), @(2, 4))
            Draw-RowsOutline $Bmp ($ax - 3) ($ay - 3) $head $DARK 1
            Draw-Rows $Bmp ($ax - 3) ($ay - 3) $head $LIGHT
            Draw-Line $Bmp ($ax - 2) ($ay - 1) ($ax + 2) ($ay - 1) $MID
        }
        'sword' {
            for ($y = $ay - 4; $y -le $ay + 6; $y++) { Set-Px $Bmp $ax $y $LIGHT; Set-Px $Bmp ($ax + 1) $y $DARK }
            Draw-Line $Bmp ($ax - 3) ($ay + 7) ($ax + 3) ($ay + 7) $DARK
            Set-Px $Bmp $ax ($ay + 8) $MID
        }
        'mace' {
            $ball = @(@(2, 1), @(1, 3), @(0, 5), @(1, 3), @(2, 1))
            Draw-RowsOutline $Bmp ($ax - 2) ($ay - 3) $ball $DARK 1
            Draw-Rows $Bmp ($ax - 2) ($ay - 3) $ball $LIGHT
            foreach ($d in @(@(0, -2), @(0, 2), @(-2, 0), @(2, 0))) { Set-Px $Bmp ($ax + $d[0]) ($ay + $d[1]) $WHITE }
        }
        'cleaver' {
            $head = @(@(0, 6), @(0, 6), @(0, 5), @(1, 4))
            Draw-RowsOutline $Bmp ($ax - 4) ($ay - 3) $head $DARK 1
            Draw-Rows $Bmp ($ax - 4) ($ay - 3) $head $LIGHT
            Set-Px $Bmp ($ax - 3) ($ay - 2) $DARK
        }
        'spear' {
            for ($y = $BB.y - 3; $y -le 24; $y++) { Set-Px $Bmp $ax $y $LIGHT; Set-Px $Bmp ($ax + 1) $y $DARK }
            $tip = @(@(3, 1), @(2, 3), @(1, 3), @(0, 1))
            Draw-RowsOutline $Bmp ($ax - 1) ($BB.y - 6) $tip $DARK 1
            Draw-Rows $Bmp ($ax - 1) ($BB.y - 6) $tip $WHITE
        }
    }
}

function Add-WizardHat {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Kind)
    if ($BB.y -lt 6) { return }
    $cx = 16; $by = $BB.y - 1
    if ($Kind -eq 'wide') {
        $brim = @(@(0, 13))
        Draw-RowsOutline $Bmp ($cx - 6) $by $brim $DARK 1
        Draw-Rows $Bmp ($cx - 6) $by $brim $LIGHT
        Draw-Rows $Bmp ($cx - 6) ($by + 1) @(@(0, 13)) $DARK
        $cone = @(@(3, 1), @(2, 3), @(1, 5), @(0, 7))
        Draw-RowsOutline $Bmp ($cx - 3) ($by - 4) $cone $DARK 1
        Draw-Rows $Bmp ($cx - 3) ($by - 4) $cone $LIGHT
        Set-Px $Bmp ($cx - 2) ($by - 3) $WHITE
    } else {
        $cone = @(@(4, 1), @(3, 3), @(2, 5), @(1, 7), @(0, 9))
        Draw-RowsOutline $Bmp ($cx - 4) ($by - 4) $cone $DARK 1
        Draw-Rows $Bmp ($cx - 4) ($by - 4) $cone $LIGHT
        Draw-Rows $Bmp ($cx - 4) $by @(@(0, 9)) $DARK
        Set-Px $Bmp ($cx + 2) ($by - 3) $WHITE
    }
}

function Add-Hood {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    if ($BB.y -lt 5) { return }
    $cx = 16; $hy = [math]::Max(0, $BB.y - 3)
    $hood = @(@(2, 5), @(1, 7), @(0, 7))
    Draw-Rows $Bmp ($cx - 3) $hy $hood $DARK
    Set-Px $Bmp $cx $hy $MID
}

function Add-Mage {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Variant)
    $sx = [math]::Min(26, $BB.right + 3)
    $top = [math]::Max(9, $BB.y)
    for ($y = $top; $y -le 24; $y++) { Set-Px $Bmp $sx $y $DARK; Set-Px $Bmp ($sx + 1) $y $LIGHT; Set-Px $Bmp ($sx + 2) $y $DARK }
    switch ($Variant) {
        'orb' {
            $oy = [math]::Max(0, $BB.y - 8)
            $orb = @(@(3, 1), @(2, 3), @(1, 5), @(0, 7), @(1, 5), @(2, 3), @(3, 1))
            Draw-RowsOutline $Bmp ($sx - 2) $oy $orb $DARK 1
            Draw-Rows $Bmp ($sx - 2) $oy $orb $LIGHT
            Draw-Rows $Bmp ($sx - 1) ($oy + 3) @(@(0, 2), @(0, 2)) $WHITE
            Set-Px $Bmp ($sx + 1) ($oy - 1) $WHITE; Set-Px $Bmp ($sx + 1) ($oy + 7) $WHITE
            Set-Px $Bmp ($sx - 3) ($oy + 3) $WHITE; Set-Px $Bmp ($sx + 5) ($oy + 3) $WHITE
            Add-WizardHat $Bmp $BB 'wide'
        }
        'crescent' {
            $oy = [math]::Max(0, $BB.y - 7)
            $cres = @(@(4, 1), @(3, 2), @(2, 2), @(1, 2), @(2, 2), @(3, 2), @(4, 1))
            Draw-Rows $Bmp ($sx - 3) $oy $cres $LIGHT
            Draw-Rows $Bmp ($sx + 1) ($oy + 1) @(@(0, 4), @(0, 4), @(0, 4)) $DARK
            Add-WizardHat $Bmp $BB 'tall'
        }
        'skull' {
            $oy = [math]::Max(0, $BB.y - 6)
            $sk = @(@(1, 3), @(0, 5), @(0, 5), @(1, 3))
            Draw-RowsOutline $Bmp ($sx - 2) $oy $sk $DARK 1
            Draw-Rows $Bmp ($sx - 2) $oy $sk $LIGHT
            Set-Px $Bmp ($sx - 1) ($oy + 1) $DARK; Set-Px $Bmp ($sx + 1) ($oy + 1) $DARK
            Add-Hood $Bmp $BB
        }
        'lantern' {
            $oy = [math]::Max(0, $BB.y - 6)
            $lan = @(@(1, 3), @(0, 4), @(0, 4), @(0, 4), @(1, 3))
            Draw-RowsOutline $Bmp ($sx - 2) $oy $lan $DARK 1
            Draw-Rows $Bmp ($sx - 2) $oy $lan $LIGHT
            Set-Px $Bmp ($sx) ($oy + 2) $WHITE
            Add-Hood $Bmp $BB
        }
        'crystal' {
            $oy = [math]::Max(0, $BB.y - 7)
            $cry = @(@(2, 1), @(1, 3), @(0, 5), @(1, 3), @(2, 3), @(3, 1))
            Draw-RowsOutline $Bmp ($sx - 2) $oy $cry $DARK 1
            Draw-Rows $Bmp ($sx - 2) $oy $cry $LIGHT
            Set-Px $Bmp ($sx) ($oy + 2) $WHITE
            Add-WizardHat $Bmp $BB 'wide'
        }
        'double' {
            $oy = [math]::Max(0, $BB.y - 7)
            foreach ($dx in @(-2, 2)) {
                $orb = @(@(1, 1), @(0, 3), @(1, 1))
                Draw-RowsOutline $Bmp ($sx + $dx) $oy $orb $DARK 1
                Draw-Rows $Bmp ($sx + $dx) $oy $orb $LIGHT
            }
            Add-WizardHat $Bmp $BB 'tall'
        }
    }
}

function Add-Quiver {
    param([System.Drawing.Bitmap]$Bmp, $BB, [int]$Count = 3)
    $qx = [math]::Min(27, $BB.right + 1); $qy = $BB.y + 4
    $quiver = @(@(1, 3), @(0, 5), @(0, 5), @(0, 5), @(0, 5), @(0, 5), @(1, 3))
    Draw-RowsOutline $Bmp $qx $qy $quiver $DARK 1
    Draw-Rows $Bmp $qx $qy $quiver $LIGHT
    for ($i = 0; $i -lt $Count; $i++) {
        Set-Px $Bmp ($qx + 1 + $i) ($qy - 1) $DARK
        Set-Px $Bmp ($qx + 1 + $i) ($qy - 2) $DARK
        Set-Px $Bmp ($qx + 1 + $i) ($qy - 3) $WHITE
    }
}

function Add-Bow {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Variant)
    $bx = [math]::Max(0, $BB.x - 7)
    $y0 = if ($Variant -eq 'longbow') { [math]::Max(2, $BB.y - 3) } else { [math]::Max(6, $BB.y + 1) }
    $y1 = 24
    for ($y = $y0; $y -le $y1; $y++) {
        $t = ($y - $y0) / [double]($y1 - $y0)
        $bulge = [int][math]::Round(3 * [math]::Sin([math]::PI * $t))
        $x = $bx + (3 - $bulge)
        Set-Px $Bmp ($x - 1) $y $DARK; Set-Px $Bmp $x $y $LIGHT; Set-Px $Bmp ($x + 1) $y $DARK
    }
    for ($y = $y0 + 1; $y -lt $y1; $y++) { Set-Px $Bmp ($bx + 5) $y 150 }
    $mid = [int](($y0 + $y1) / 2)
    Draw-Line $Bmp ($bx + 3) $mid ($bx + 8) $mid $LIGHT
    Set-Px $Bmp ($bx + 8) $mid $WHITE
    if ($Variant -eq 'bow') { Add-Quiver $Bmp $BB 3 }
}

function Add-Javelin {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    for ($i = 0; $i -lt 3; $i++) {
        $x = [math]::Min(28, $BB.right + 1 + $i * 2)
        for ($y = $BB.y - 2; $y -le $BB.y + 9; $y++) { Set-Px $Bmp $x $y $LIGHT; Set-Px $Bmp ($x + 1) $y $DARK }
        $tip = @(@(2, 1), @(1, 1), @(0, 1))
        Draw-Rows $Bmp ($x - 1) ($BB.y - 5) $tip $WHITE
    }
    $sx = [math]::Max(0, $BB.x - 7)
    for ($y = $BB.y - 3; $y -le 24; $y++) { Set-Px $Bmp $sx $y $DARK; Set-Px $Bmp ($sx + 1) $y $LIGHT }
    $tip2 = @(@(2, 1), @(1, 3), @(0, 1))
    Draw-RowsOutline $Bmp ($sx - 1) ($BB.y - 7) $tip2 $DARK 1
    Draw-Rows $Bmp ($sx - 1) ($BB.y - 7) $tip2 $WHITE
}

function Add-Sling {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    $cx = 16
    Draw-Line $Bmp ($BB.x - 3) ($BB.y + 8) ($cx - 1) ($BB.y + 15) $DARK
    Draw-Line $Bmp ($BB.right + 3) ($BB.y + 8) ($cx + 1) ($BB.y + 15) $DARK
    $pouch = @(@(1, 3), @(0, 5), @(1, 3))
    Draw-RowsOutline $Bmp ($cx - 2) ($BB.y + 15) $pouch $DARK 1
    Draw-Rows $Bmp ($cx - 2) ($BB.y + 15) $pouch $MID
    Set-Px $Bmp ($cx) ($BB.y + 16) $WHITE
}

function Add-Dagger {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Variant)
    if ($Variant -eq 'dual') {
        foreach ($side in @('L', 'R')) {
            if ($side -eq 'L') { $x = [math]::Max(1, $BB.x - 3) } else { $x = [math]::Min(28, $BB.right + 2) }
            $y0 = $BB.y + 9; $y1 = [math]::Min(22, $BB.y + 16)
            for ($y = $y0; $y -le $y1; $y++) { Set-Px $Bmp ($x - 1) $y $DARK; Set-Px $Bmp $x $y $LIGHT; Set-Px $Bmp ($x + 1) $y $DARK }
            for ($i = -1; $i -le 1; $i++) { Set-Px $Bmp ($x + $i) ($y1 + 1) $DARK }
        }
    } else {
        $topX = [math]::Min(28, $BB.right + 1); $botX = [math]::Max(2, $BB.x - 2)
        $y0 = $BB.y + 8; $y1 = [math]::Min(23, $BB.y + 15)
        for ($y = $y0; $y -le $y1; $y++) {
            $t = ($y - $y0) / [double]($y1 - $y0)
            $x = [int][math]::Round($topX + ($botX - $topX) * $t)
            Set-Px $Bmp ($x - 1) $y $DARK; Set-Px $Bmp $x $y $LIGHT; Set-Px $Bmp ($x + 1) $y $DARK
        }
        for ($i = -2; $i -le 2; $i++) { Set-Px $Bmp ($botX + $i) ($y1 + 1) $DARK }
        Set-Px $Bmp $botX ($y1 + 1) $LIGHT
        Set-Px $Bmp $botX ($y1 + 2) $MID
    }
    Add-Hood $Bmp $BB
}

function Add-Accessory {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Acc)
    switch ($Acc) {
        'antlers' {
            $cx = 16; $ty = [math]::Max(0, $BB.y - 4)
            foreach ($side in @(-1, 1)) {
                $bx = $cx + $side * 4
                Draw-Line $Bmp $bx $ty ($bx + $side * 2) ($ty + 3) $DARK
                Draw-Line $Bmp $bx ($ty + 1) ($bx + $side * 3) ($ty) $DARK
                Set-Px $Bmp ($bx + $side * 2) ($ty + 3) $LIGHT
            }
        }
        'ledger' {
            $lx = [math]::Max(1, $BB.x - 5); $ly = $BB.y + 11
            $book = @(@(0, 3), @(0, 3), @(0, 3), @(0, 3))
            Draw-RowsOutline $Bmp $lx $ly $book $DARK 0
            Draw-Rows $Bmp $lx $ly $book $MID
            Draw-Line $Bmp ($lx + 1) ($ly + 1) ($lx + 2) ($ly + 1) $WHITE
        }
        'horn' {
            $hx = 16; $hy = $BB.y + 6
            Draw-Line $Bmp ($hx + 3) $hy ($hx + 5) ($hy - 2) $MID
            Set-Px $Bmp ($hx + 5) ($hy - 2) $LIGHT
        }
        'flower' {
            $fx = 16; $fy = [math]::Max(0, $BB.y - 2)
            Set-Px $Bmp $fx $fy $WHITE; Set-Px $Bmp ($fx - 1) $fy $LIGHT; Set-Px $Bmp ($fx + 1) $fy $LIGHT
            Set-Px $Bmp $fx ($fy + 1) $LIGHT
        }
    }
}

# ---------------- boss details ----------------
function Add-BossDetail {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Style)
    $cx = 16
    switch ($Style) {
        'vines' {
            Draw-Line $Bmp ($BB.x - 2) ($BB.y + 3) ($BB.x - 2) ($BB.y + 14) $DARK
            Draw-Line $Bmp ($BB.right + 2) ($BB.y + 5) ($BB.right + 2) ($BB.y + 16) $DARK
            for ($i = 0; $i -lt 3; $i++) { Set-Px $Bmp ($BB.x - 3) ($BB.y + 5 + $i * 4) $LIGHT; Set-Px $Bmp ($BB.right + 3) ($BB.y + 7 + $i * 3) $LIGHT }
            Draw-Rows $Bmp ($cx - 4) ($BB.y - 1) @(@(0, 3), @(0, 5), @(0, 3)) $DARK
            # thorn crown + shoulder spikes
            Draw-Rows $Bmp ($cx - 3) ($BB.y + 1) @(@(0, 7), @(0, 7)) $DARK
            Set-Px $Bmp ($cx - 3) ($BB.y) $WHITE; Set-Px $Bmp $cx ($BB.y - 1) $WHITE; Set-Px $Bmp ($cx + 3) ($BB.y) $WHITE
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $px = [math]::Max(0, $BB.x - 2) } else { $px = [math]::Min(28, $BB.right) }
                Draw-Rows $Bmp $px ($BB.y + 6) @(@(0, 2), @(0, 2), @(0, 2)) $DARK
                Set-Px $Bmp ($px + 1) ($BB.y + 5) $WHITE
            }
        }
        'antlers' {
            $ty = [math]::Max(0, $BB.y - 4)
            foreach ($side in @(-1, 1)) {
                $bx = $cx + $side * 4
                Draw-Line $Bmp $bx $ty ($bx + $side * 4) ($ty + 5) $DARK
                Draw-Line $Bmp ($bx + $side * 2) ($ty + 2) ($bx + $side * 4) ($ty - 1) $DARK
                Draw-Line $Bmp $bx ($ty + 1) ($bx + $side * 2) ($ty + 1) $LIGHT
            }
            # glowing core
            Draw-RowsOutline $Bmp ($cx - 1) ($BB.y + 10) @(@(0, 3), @(0, 3)) $DARK 1
            Draw-Rows $Bmp ($cx - 1) ($BB.y + 10) @(@(0, 3), @(0, 3)) $WHITE
        }
        'one_eye' {
            Draw-RowsOutline $Bmp ($cx - 2) ($BB.y + 2) @(@(0, 5), @(0, 5), @(0, 5), @(0, 5), @(0, 5)) $DARK 1
            Draw-Rows $Bmp ($cx - 2) ($BB.y + 2) @(@(0, 5), @(0, 5), @(0, 5), @(0, 5), @(0, 5)) $LIGHT
            Set-Px $Bmp ($cx - 1) ($BB.y + 4) $WHITE; Set-Px $Bmp $cx ($BB.y + 4) $WHITE
            Set-Px $Bmp ($cx - 1) ($BB.y + 3) $DARK; Set-Px $Bmp $cx ($BB.y + 3) $DARK
            # horns
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $hx = $BB.x + 2 } else { $hx = $BB.right - 2 }
                Draw-Rows $Bmp $hx ($BB.y - 1) @(@(0, 2), @(0, 2), @(0, 2)) $DARK
                Set-Px $Bmp ($hx + 1) ($BB.y) $LIGHT
            }
        }
        'stone' {
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $px = [math]::Max(0, $BB.x - 1) } else { $px = [math]::Min(27, $BB.right - 2) }
                Draw-RowsOutline $Bmp $px ($BB.y + 5) @(@(0, 4), @(0, 4), @(0, 4)) $DARK 0
                Draw-Rows $Bmp $px ($BB.y + 5) @(@(0, 4), @(0, 4)) $MID
            }
            Draw-Line $Bmp ($BB.x + 2) ($BB.y + 12) ($BB.right - 2) ($BB.y + 12) $DARK
            # square stone crown
            Draw-Rows $Bmp ($cx - 3) ($BB.y + 1) @(@(0, 7), @(0, 7)) $MID
            Set-Px $Bmp ($cx - 2) ($BB.y + 2) $WHITE; Set-Px $Bmp $cx ($BB.y + 2) $WHITE; Set-Px $Bmp ($cx + 2) ($BB.y + 2) $WHITE
        }
        'twin_skulls' {
            foreach ($side in @(-1, 1)) {
                $sx = $cx + $side * 5 - 1
                Draw-RowsOutline $Bmp $sx ($BB.y + 5) @(@(0, 3), @(0, 3), @(0, 3)) $DARK 0
                Draw-Rows $Bmp $sx ($BB.y + 5) @(@(0, 3), @(0, 3), @(0, 3)) $LIGHT
                Set-Px $Bmp ($sx + 1) ($BB.y + 6) $DARK
            }
            Draw-Rows $Bmp ($cx - 2) ($BB.y + 1) @(@(0, 5), @(0, 5)) $DARK
            Set-Px $Bmp ($cx - 1) ($BB.y) $WHITE; Set-Px $Bmp $cx ($BB.y) $WHITE; Set-Px $Bmp ($cx + 1) ($BB.y) $WHITE
        }
        'bone_crown' {
            Draw-Rows $Bmp ($cx - 3) ($BB.y + 1) @(@(0, 7), @(0, 7)) $DARK
            for ($i = 0; $i -lt 3; $i++) { Set-Px $Bmp ($cx - 2 + $i * 2) ($BB.y) $WHITE }
            for ($i = 0; $i -lt 4; $i++) { Draw-Line $Bmp ($cx - 4) ($BB.y + 10 + $i * 2) ($cx + 4) ($BB.y + 10 + $i * 2) $DARK }
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $px = [math]::Max(0, $BB.x) } else { $px = [math]::Min(29, $BB.right - 1) }
                Set-Px $Bmp $px ($BB.y + 6) $WHITE; Set-Px $Bmp ($px + 1) ($BB.y + 7) $LIGHT
            }
        }
        'fur' {
            Draw-Rows $Bmp ($cx - 4) ($BB.y + 7) @(@(0, 9), @(0, 9)) $DARK
            for ($i = 0; $i -lt 4; $i++) { Set-Px $Bmp ($cx - 3 + $i * 2) ($BB.y + 8) $LIGHT }
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $hx = $BB.x + 2 } else { $hx = $BB.right - 2 }
                Draw-Rows $Bmp $hx ($BB.y - 1) @(@(0, 2), @(0, 2), @(0, 2)) $DARK
                Set-Px $Bmp ($hx + 1) ($BB.y) $LIGHT
            }
        }
        'star_breaker' {
            $sy = [math]::Max(0, $BB.y - 3)
            $star = @(@(3, 1), @(2, 2), @(1, 5), @(2, 2), @(3, 1))
            Draw-RowsOutline $Bmp ($cx - 3) $sy $star $DARK 1
            Draw-Rows $Bmp ($cx - 3) $sy $star $WHITE
            $ax = [math]::Min(27, $BB.right + 2)
            for ($y = $BB.y + 6; $y -le 24; $y++) { Set-Px $Bmp $ax $y $LIGHT; Set-Px $Bmp ($ax + 1) $y $DARK }
            Draw-RowsOutline $Bmp ($ax - 3) ($BB.y + 3) @(@(3, 2), @(2, 4), @(1, 5), @(2, 4), @(3, 2)) $DARK 1
            Draw-Rows $Bmp ($ax - 3) ($BB.y + 3) @(@(3, 2), @(2, 4), @(1, 5), @(2, 4), @(3, 2)) $LIGHT
            # chest star
            Draw-Rows $Bmp ($cx - 2) ($BB.y + 9) @(@(2, 1), @(1, 3), @(0, 3), @(1, 3), @(2, 1)) $LIGHT
        }
        'star_warden' {
            $sy = [math]::Max(0, $BB.y - 3)
            $ring = @(@(3, 1), @(2, 3), @(1, 3), @(0, 3), @(1, 3), @(2, 3), @(3, 1))
            Draw-RowsOutline $Bmp ($cx - 4) $sy $ring $DARK 0
            Draw-Rows $Bmp ($cx - 4) $sy $ring $WHITE
            Add-Shield $Bmp $BB
            Set-Px $Bmp ($BB.x - 5) ($BB.y + 10) $WHITE
            Set-Px $Bmp ($BB.x - 4) ($BB.y + 10) $LIGHT
            Set-Px $Bmp ($BB.x - 6) ($BB.y + 10) $LIGHT
        }
    }
}

# ---------------- configs ----------------
$UnitDefs = @(
    @{ id='unit_warrior_01'; race='兽人'; cls='战士'; body='orc_warrior'; fw=16; fh=20; h=21; variant='axe' },
    @{ id='unit_boar_rider'; race='兽人'; cls='战士'; body='masked_orc'; fw=16; fh=20; h=21; variant='hammer' },
    @{ id='unit_mage_apprentice'; race='兽人'; cls='法师'; body='orc_shaman'; fw=16; fh=20; h=21; variant='orb' },
    @{ id='unit_legend_quartermaster'; race='兽人'; cls='法师'; body='masked_orc'; fw=16; fh=20; h=21; variant='crescent' },
    @{ id='unit_shadow_blade'; race='兽人'; cls='刺客'; body='goblin'; fw=16; fh=16; h=17; variant='dual' },
    @{ id='unit_spear_headhunter'; race='兽人'; cls='游侠'; body='orc_warrior'; fw=16; fh=20; h=21; variant='javelin' },

    @{ id='unit_wolf_pup'; race='野兽'; cls='刺客'; body='wogol'; fw=16; fh=20; h=19; variant='dagger' },
    @{ id='unit_ambush_panther'; race='野兽'; cls='刺客'; body='chort'; fw=16; fh=24; h=21; variant='dual' },
    @{ id='unit_beast_archer'; race='野兽'; cls='游侠'; body='wogol'; fw=16; fh=20; h=19; variant='bow' },
    @{ id='unit_ranger_01'; race='野兽'; cls='游侠'; body='muddy'; fw=16; fh=16; h=18; variant='longbow' },
    @{ id='unit_legend_thornhart'; race='野兽'; cls='游侠'; body='wogol'; fw=16; fh=20; h=19; variant='longbow'; acc='antlers' },
    @{ id='unit_fairy_druid'; race='野兽'; cls='法师'; body='swampy'; fw=16; fh=16; h=18; variant='orb'; acc='flower' },

    @{ id='unit_skeleton_soldier'; race='亡灵'; cls='战士'; body='skelet'; fw=16; fh=16; h=20; variant='axe' },
    @{ id='unit_death_knight'; race='亡灵'; cls='战士'; body='big_zombie'; fw=32; fh=34; h=25; variant='sword' },
    @{ id='unit_debt_collector'; race='亡灵'; cls='刺客'; body='tiny_zombie'; fw=16; fh=16; h=17; variant='knife'; acc='ledger' },
    @{ id='unit_wraith'; race='亡灵'; cls='法师'; body='necromancer'; fw=16; fh=20; h=21; variant='skull' },
    @{ id='unit_lamplighter'; race='亡灵'; cls='法师'; body='zombie'; fw=16; fh=16; h=20; variant='lantern' },
    @{ id='unit_bowstring_archer'; race='亡灵'; cls='游侠'; body='ice_zombie'; fw=16; fh=16; h=20; variant='bow' },

    @{ id='unit_frost_giant'; race='巨人'; cls='战士'; body='ogre'; fw=32; fh=32; h=25; variant='axe' },
    @{ id='unit_glacial_giant'; race='巨人'; cls='战士'; body='big_zombie'; fw=32; fh=34; h=25; variant='hammer' },
    @{ id='unit_frost_imp'; race='巨人'; cls='刺客'; body='imp'; fw=16; fh=16; h=17; variant='dagger' },
    @{ id='unit_assassin_01'; race='巨人'; cls='刺客'; body='ogre'; fw=32; fh=32; h=25; variant='dual' },
    @{ id='unit_rubble_slinger'; race='巨人'; cls='游侠'; body='big_zombie'; fw=32; fh=34; h=25; variant='sling' },
    @{ id='unit_legend_warsong_singer'; race='巨人'; cls='法师'; body='ogre'; fw=32; fh=32; h=25; variant='crystal'; acc='horn' }
)
$BossDefs = @(
    @{ id='boss_thorn_mother'; race='植物'; body='swampy'; fw=16; fh=16; h=26; style='vines' },
    @{ id='boss_thorn_true'; race='植物'; body='swampy'; fw=16; fh=16; h=26; style='antlers' },
    @{ id='boss_one_eye'; race='独眼'; body='ogre'; fw=32; fh=32; h=26; style='one_eye' },
    @{ id='boss_tomb_colossus'; race='构造'; body='big_zombie'; fw=32; fh=34; h=26; style='stone' },
    @{ id='boss_twin_lich'; race='亡灵'; body='necromancer'; fw=16; fh=20; h=26; style='twin_skulls' },
    @{ id='boss_bone_duke'; race='亡灵'; body='big_zombie'; fw=32; fh=34; h=26; style='bone_crown' },
    @{ id='boss_frost_howler'; race='巨人'; body='big_zombie'; fw=32; fh=34; h=26; style='fur' },
    @{ id='boss_star_breaker'; race='巨人'; body='ogre'; fw=32; fh=32; h=26; style='star_breaker' },
    @{ id='boss_star_warden'; race='巨人'; body='ogre'; fw=32; fh=32; h=26; style='star_warden' }
)

$Frames = @(
    @{ anim='idle'; frame=0; type='idle'; idx=0; op='plain' },
    @{ anim='idle'; frame=1; type='idle'; idx=1; op='plain' },
    @{ anim='walk'; frame=0; type='run'; idx=1; op='plain' },
    @{ anim='walk'; frame=1; type='run'; idx=3; op='plain' },
    @{ anim='attack'; frame=0; type='idle'; idx=0; op='lean_back' },
    @{ anim='attack'; frame=1; type='run'; idx=2; op='lunge' },
    @{ anim='attack'; frame=2; type='idle'; idx=1; op='lean_fwd' },
    @{ anim='cast'; frame=0; type='idle'; idx=0; op='plain' },
    @{ anim='cast'; frame=1; type='idle'; idx=0; op='cast' },
    @{ anim='death'; frame=0; type='idle'; idx=0; op='plain' },
    @{ anim='death'; frame=1; type='idle'; idx=0; op='fall' },
    @{ anim='death'; frame=2; type='idle'; idx=0; op='dead' }
)

# ---------------- generate ----------------
$manifest = New-Object System.Collections.Generic.List[object]
function Invoke-Op {
    param([System.Drawing.Bitmap]$Bmp, [string]$Op)
    switch ($Op) {
        'plain' { return $Bmp }
        'lean_back' { return Add-Lean $Bmp -1 }
        'lean_fwd' { return Add-Lean $Bmp 1 }
        'lunge' { return Add-Lean $Bmp 2 }
        'fall' { return Add-Lean (Add-Squash $Bmp 0.62) 2 }
        'dead' { return Add-Rotate90 $Bmp }
        'cast' { return Add-Rim $Bmp }
    }
    return $Bmp
}

function Process-Unit {
    param($Def, [bool]$IsBoss)
    $baseFrames = @{}
    foreach ($f in $Frames) {
        $key = '{0}|{1}' -f $f.type, $f.idx
        if (-not $baseFrames.ContainsKey($key)) {
            $baseFrames[$key] = Get-BodyBase $Def.body $Def.fw $Def.fh $Def.h $f.type $f.idx
        }
    }
    foreach ($f in $Frames) {
        $bmp = [System.Drawing.Bitmap]::new($baseFrames[('{0}|{1}' -f $f.type, $f.idx)])
        $bb = Get-BBox $bmp
        if ($IsBoss) {
            Add-BossDetail $bmp $bb $Def.style
        } else {
            switch ($Def.cls) {
                '战士' { Add-Warrior $bmp $bb $Def.variant }
                '法师' { Add-Mage $bmp $bb $Def.variant }
                '游侠' {
                    switch ($Def.variant) {
                        'bow' { Add-Bow $bmp $bb 'bow' }
                        'longbow' { Add-Bow $bmp $bb 'longbow' }
                        'javelin' { Add-Javelin $bmp $bb }
                        'sling' { Add-Sling $bmp $bb }
                    }
                }
                '刺客' { Add-Dagger $bmp $bb $Def.variant }
            }
            if ($Def.acc) { Add-Accessory $bmp $bb $Def.acc }
            Add-RaceMarker $bmp $bb $Def.race
        }
        $t = Invoke-Op $bmp $f.op
        if (-not [object]::ReferenceEquals($t, $bmp)) { $bmp.Dispose(); $bmp = $t }
        $val = switch ($f.op) { 'lean_back' { 0.92 } 'lunge' { 1.06 } 'cast' { 1.06 } 'fall' { 0.85 } 'dead' { 0.75 } default { 1.0 } }
        if ($val -ne 1.0) { $adj = Adjust-Value $bmp $val; $bmp.Dispose(); $bmp = $adj }
        $bottom = Get-BottomRow $bmp
        if ($bottom -ne 26) { throw "baseline $($Def.id) $($f.anim)_$($f.frame) = $bottom" }
        $key = '{0}_{1}_{2}' -f $Def.id, $f.anim, $f.frame
        $path = Join-Path $UnitDir ($key + '.png')
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $manifest.Add([pscustomobject]@{
            key = $key; file = 'units/' + $key + '.png'; width = 32; height = 32
            sha256 = (Get-FileHash -Algorithm SHA256 $path).Hash.ToLower()
            unit_id = $Def.id; race = $Def.race; class = if ($IsBoss) { 'Boss' } else { $Def.cls }
            body = $Def.body; target_h = $Def.h
            variant = if ($IsBoss) { $Def.style } else { $Def.variant }
            accessory = if ($Def.acc) { $Def.acc } else { '' }
            anim = $f.anim; frame = $f.frame; op = $f.op
            bottom_row = $bottom; colors = (Get-ColorCount $bmp)
            source_pack = $Pack.name; source_url = $Pack.url; author = $Pack.author
            license = $Pack.license; attribution = $Pack.attribution
        })
        if ($f.anim -eq 'idle' -and $f.frame -eq 0 -and -not $IsBoss) {
            # colour reference: raw colour body, no props (comparison only)
            $data = $script:BodyCache["data|$($Def.body)"]
            $src = $data.idle[0]
            $cbb = Get-BBox $src
            $crop = [System.Drawing.Bitmap]::new($cbb.w, $cbb.h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $g = [System.Drawing.Graphics]::FromImage($crop)
            $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
            $g.DrawImage($src, [System.Drawing.Rectangle]::new(0, 0, $cbb.w, $cbb.h), [System.Drawing.Rectangle]::new($cbb.x, $cbb.y, $cbb.w, $cbb.h), [System.Drawing.GraphicsUnit]::Pixel)
            $g.Dispose()
            $cs = Resize-Nearest $crop ([int][math]::Round($cbb.w * $Def.h / $cbb.h)) $Def.h
            $cc = Place-OnCanvas $cs 26
            $cc.Save((Join-Path $ColorDir ($key + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
            $crop.Dispose(); $cs.Dispose(); $cc.Dispose()
        }
        $bmp.Dispose()
    }
    foreach ($v in $baseFrames.Values) { $v.Dispose() }
}

foreach ($d in $UnitDefs) { Process-Unit $d $false }
foreach ($d in $BossDefs) { Process-Unit $d $true }

$csv = $manifest | ConvertTo-Csv -NoTypeInformation
[IO.File]::WriteAllText((Join-Path $OutDir 'manifest.csv'), ($csv -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($true)))
[IO.File]::WriteAllText((Join-Path $OutDir 'manifest.json'), ($manifest | ConvertTo-Json -Depth 4), (New-Object Text.UTF8Encoding($false)))

# ---------------- previews ----------------
$Font = [System.Drawing.Font]::new('Consolas', 9)
$FontSm = [System.Drawing.Font]::new('Consolas', 7)
$FontTiny = [System.Drawing.Font]::new('Consolas', 6)

function New-Sheet {
    param([int]$W, [int]$H, [int]$BgR = 20, [int]$BgG = 20, [int]$BgB = 26)
    $bmp = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb($BgR, $BgG, $BgB))
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    return @{ bmp = $bmp; g = $g }
}

# roster 32px (4x3 rows x 6 cols)
$cell = 32 * 4; $gut = 120; $hdr = 18; $gap = 6
$rows = 4; $cols = 6
$shR = New-Sheet ($gut + $cols * ($cell + $gap) + 10) ($hdr + $rows * ($cell + $gap) + 10)
$g = $shR.g
for ($c = 0; $c -lt $cols; $c++) { $g.DrawString(('slot {0}' -f ($c + 1)), $FontSm, [System.Drawing.Brushes]::LightGray, ($gut + $c * ($cell + $gap) + 2), 3) }
for ($r = 0; $r -lt $rows; $r++) {
    $race = @('兽人', '野兽', '亡灵', '巨人')[$r]
    $g.DrawString($race, $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    $list = @($UnitDefs | Where-Object { $_.race -eq $race })
    for ($c = 0; $c -lt $list.Count; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($list[$c].id + '_idle_0.png')))
        $g.DrawImage($b, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
        $g.DrawString($list[$c].id.Replace('unit_',''), $FontSm, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), ($hdr + $r * ($cell + $gap) + $cell + 1))
        $b.Dispose()
    }
}
$g.Dispose(); $shR.bmp.Save((Join-Path $PrevDir 'roster_24_32.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shR.bmp.Dispose()

# roster 24px (8x)
$cell = 24 * 8; $gut = 120; $hdr = 18; $gap = 10
$shR2 = New-Sheet ($gut + 6 * ($cell + $gap) + 10) ($hdr + 4 * ($cell + $gap) + 10)
$g = $shR2.g
for ($r = 0; $r -lt 4; $r++) {
    $race = @('兽人', '野兽', '亡灵', '巨人')[$r]
    $g.DrawString($race, $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    $list = @($UnitDefs | Where-Object { $_.race -eq $race })
    for ($c = 0; $c -lt $list.Count; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($list[$c].id + '_idle_0.png')))
        $s = Resize-Nearest $b 24 24
        $g.DrawImage($s, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
        $b.Dispose(); $s.Dispose()
    }
}
$g.Dispose(); $shR2.bmp.Save((Join-Path $PrevDir 'roster_24_24px.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shR2.bmp.Dispose()

# boss roster 32px (3x3)
$cell = 32 * 5; $gut = 150; $hdr = 18; $gap = 8
$shB = New-Sheet ($gut + 3 * ($cell + $gap) + 10) ($hdr + 3 * ($cell + $gap) + 10)
$g = $shB.g
for ($i = 0; $i -lt $BossDefs.Count; $i++) {
    $r = [int][math]::Floor($i / 3); $c = $i % 3
    $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($BossDefs[$i].id + '_idle_0.png')))
    $g.DrawImage($b, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
    $g.DrawString($BossDefs[$i].id.Replace('boss_',''), $FontSm, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), ($hdr + $r * ($cell + $gap) + $cell + 1))
    $b.Dispose()
}
$g.Dispose(); $shB.bmp.Save((Join-Path $PrevDir 'roster_bosses_32.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shB.bmp.Dispose()

# per-race contact sheets (6 units x 12 frames, 2x)
foreach ($race in @('兽人', '野兽', '亡灵', '巨人')) {
    $list = @($UnitDefs | Where-Object { $_.race -eq $race })
    $cell = 32 * 2; $gut = 150; $hdr = 16; $gap = 4
    $sh = New-Sheet ($gut + $list.Count * ($cell + $gap) + 10) ($hdr + 12 * ($cell + $gap) + 10)
    $g = $sh.g
    for ($c = 0; $c -lt $list.Count; $c++) { $g.DrawString($list[$c].id.Replace('unit_',''), $FontTiny, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), 2) }
    for ($f = 0; $f -lt 12; $f++) {
        $fr = $Frames[$f]
        $g.DrawString(('{0}_{1}' -f $fr.anim, $fr.frame), $FontSm, [System.Drawing.Brushes]::LightGray, 4, ($hdr + $f * ($cell + $gap) + [int]($cell / 2) - 5))
        for ($c = 0; $c -lt $list.Count; $c++) {
            $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ('{0}_{1}_{2}.png' -f $list[$c].id, $fr.anim, $fr.frame)))
            $g.DrawImage($b, ($gut + $c * ($cell + $gap)), ($hdr + $f * ($cell + $gap)), $cell, $cell)
            $b.Dispose()
        }
    }
    $g.Dispose(); $sh.bmp.Save((Join-Path $PrevDir ('contact_' + $race + '.png')), [System.Drawing.Imaging.ImageFormat]::Png); $sh.bmp.Dispose()
}

# 3-star tint roster 24 (6x4, 4x)
$cell = 32 * 4; $gut = 120; $hdr = 18; $gap = 6
$shT = New-Sheet ($gut + 6 * ($cell + $gap) + 10) ($hdr + 4 * ($cell + $gap) + 10) 30 28 24
$g = $shT.g
for ($r = 0; $r -lt 4; $r++) {
    $race = @('兽人', '野兽', '亡灵', '巨人')[$r]
    $g.DrawString($race, $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    $list = @($UnitDefs | Where-Object { $_.race -eq $race })
    for ($c = 0; $c -lt $list.Count; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($list[$c].id + '_idle_0.png')))
        $t = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($y = 0; $y -lt 32; $y++) { for ($x = 0; $x -lt 32; $x++) { $p = $b.GetPixel($x, $y); if ($p.A -gt 0) { $t.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($p.A, [int]($p.R * 0.72), [int]($p.G * 0.40), [int]($p.B * 0.96))) } } }
        $g.DrawImage($t, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
        $b.Dispose(); $t.Dispose()
    }
}
$g.Dispose(); $shT.bmp.Save((Join-Path $PrevDir 'star_tints_24.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shT.bmp.Dispose()

# boss roster 24px
$cell = 24 * 8; $gut = 150; $hdr = 18; $gap = 10
$shB2 = New-Sheet ($gut + 3 * ($cell + $gap) + 10) ($hdr + 3 * ($cell + $gap) + 10)
$g = $shB2.g
for ($i = 0; $i -lt $BossDefs.Count; $i++) {
    $r = [int][math]::Floor($i / 3); $c = $i % 3
    $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($BossDefs[$i].id + '_idle_0.png')))
    $s = Resize-Nearest $b 24 24
    $g.DrawImage($s, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
    $g.DrawString($BossDefs[$i].id.Replace('boss_', ''), $Font, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), ($hdr + $r * ($cell + $gap) + $cell + 1))
    $b.Dispose(); $s.Dispose()
}
$g.Dispose(); $shB2.bmp.Save((Join-Path $PrevDir 'roster_bosses_24px.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shB2.bmp.Dispose()

# boss contact sheet (9 units x 12 frames, 2x)
$cell = 32 * 2; $gut = 150; $hdr = 16; $gap = 4
$shBC = New-Sheet ($gut + $BossDefs.Count * ($cell + $gap) + 10) ($hdr + 12 * ($cell + $gap) + 10)
$g = $shBC.g
for ($c = 0; $c -lt $BossDefs.Count; $c++) { $g.DrawString($BossDefs[$c].id.Replace('boss_', ''), $FontTiny, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), 2) }
for ($f = 0; $f -lt 12; $f++) {
    $fr = $Frames[$f]
    $g.DrawString(('{0}_{1}' -f $fr.anim, $fr.frame), $FontTiny, [System.Drawing.Brushes]::LightGray, 4, ($hdr + $f * ($cell + $gap) + [int]($cell / 2) - 5))
    for ($c = 0; $c -lt $BossDefs.Count; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ('{0}_{1}_{2}.png' -f $BossDefs[$c].id, $fr.anim, $fr.frame)))
        $g.DrawImage($b, ($gut + $c * ($cell + $gap)), ($hdr + $f * ($cell + $gap)), $cell, $cell)
        $b.Dispose()
    }
}
$g.Dispose(); $shBC.bmp.Save((Join-Path $PrevDir 'contact_bosses.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shBC.bmp.Dispose()

$bad = @($manifest | Where-Object { $_.bottom_row -ne 26 }).Count
Write-Output ("OK frames={0} units={1} bosses={2} baseline_bad={3} colorsMax={4}" -f $manifest.Count, $UnitDefs.Count, $BossDefs.Count, $bad, (($manifest | Measure-Object colors -Maximum).Maximum))
Write-Output ("OUT " + $OutDir)
