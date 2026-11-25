# 16-cell class/race validation grid (not batch-2 final assets)
# 4 bodies x 4 class silhouettes, 6-grey tint-ready pipeline, 32x32, feet y=26.
# Run: powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\make_validation_grid.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SrcDir = Join-Path $Root 'assets_src\sourced\0x72-dungeontileset-ii'
$OutDir = Join-Path $Root 'assets_src\validation_grid'
$UnitDir = Join-Path $OutDir 'units'
$PrevDir = Join-Path $OutDir 'previews'
New-Item -ItemType Directory -Force -Path $UnitDir, $PrevDir | Out-Null

$DARK = 40; $MID = 145; $LIGHT = 225; $WHITE = 255

$UnitDefs = @(
    @{ key = 'orc';    label = 'orc';    src = 'monsters\orc_warrior_idle_anim.png';    fw = 16; fh = 20; targetH = 21; race = '兽人' },
    @{ key = 'wogol';  label = 'wogol';  src = 'monsters\wogol_idle_anim.png';          fw = 16; fh = 20; targetH = 19; race = '野兽' },
    @{ key = 'skelet'; label = 'skelet'; src = 'monsters\skelet_idle_anim.png';          fw = 16; fh = 16; targetH = 20; race = '亡灵' },
    @{ key = 'ogre';   label = 'ogre';   src = 'monsters\ogre_idle_anim.png';            fw = 32; fh = 32; targetH = 25; race = '巨人' }
)
$ClassDefs = @(
    @{ key = 'warrior';  label = 'W'; name = '战士' },
    @{ key = 'mage';     label = 'M'; name = '法师' },
    @{ key = 'ranger';   label = 'R'; name = '游侠' },
    @{ key = 'assassin'; label = 'A'; name = '刺客' }
)

# ---------- image helpers ----------
function Get-IdleFrame {
    param([string]$Path, [int]$Fw, [int]$Fh)
    $sheet = [System.Drawing.Bitmap]::new($Path)
    $bmp = [System.Drawing.Bitmap]::new($Fw, $Fh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.DrawImage($sheet, [System.Drawing.Rectangle]::new(0, 0, $Fw, $Fh), [System.Drawing.Rectangle]::new(0, 0, $Fw, $Fh), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose(); $sheet.Dispose()
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
    param([System.Drawing.Bitmap]$Bmp)
    $lum = @{}
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $k = '{0},{1},{2}' -f $c.R, $c.G, $c.B
            if (-not $lum.ContainsKey($k)) { $lum[$k] = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B }
        }
    }
    $sorted = @($lum.GetEnumerator() | Sort-Object Value)
    $n = $sorted.Count
    $lut = @{}
    for ($i = 0; $i -lt $n; $i++) {
        $t = if ($n -le 1) { 1.0 } else { $i / ($n - 1) }
        $lut[$sorted[$i].Key] = [int][math]::Round((0.12 + 0.85 * [math]::Pow($t, 0.70)) * 255)
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

function Draw-Rows {
    param([System.Drawing.Bitmap]$Bmp, [int]$X0, [int]$Y0, [array]$Rows, [int]$Value)
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $start = $Rows[$i][0]; $w = $Rows[$i][1]
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

function Squash-Bottom {
    param([System.Drawing.Bitmap]$Bmp, [double]$Factor)
    $dst = [System.Drawing.Bitmap]::new(32, 32, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($sy = 0; $sy -le 26; $sy++) {
        $ty = 26 - [int][math]::Round((26 - $sy) * $Factor)
        for ($x = 0; $x -lt 32; $x++) {
            $c = $Bmp.GetPixel($x, $sy)
            if ($c.A -gt 0 -and $ty -ge 0) { $dst.SetPixel($x, $ty, $c) }
        }
    }
    return $dst
}

# ---------- class props ----------
function Set-PxOver {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$V)
    if ($X -lt 0 -or $X -gt 31 -or $Y -lt 0 -or $Y -gt 31) { return }
    $Bmp.SetPixel($X, $Y, [System.Drawing.Color]::FromArgb(255, $V, $V, $V))
}

function Draw-RowsOutline {
    param([System.Drawing.Bitmap]$Bmp, [int]$X0, [int]$Y0, [array]$Rows, [int]$Value, [int]$Grow = 1)
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $start = $Rows[$i][0] - $Grow; $w = $Rows[$i][1] + 2 * $Grow
        for ($j = 0; $j -lt $w; $j++) { Set-Px $Bmp ($X0 + $start + $j) ($Y0 + $i) $Value }
    }
}

function Draw-RowsOver {
    param([System.Drawing.Bitmap]$Bmp, [int]$X0, [int]$Y0, [array]$Rows, [int]$Value)
    for ($i = 0; $i -lt $Rows.Count; $i++) {
        $start = $Rows[$i][0]; $w = $Rows[$i][1]
        for ($j = 0; $j -lt $w; $j++) { Set-PxOver $Bmp ($X0 + $start + $j) ($Y0 + $i) $Value }
    }
}

function Add-WarriorProps {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    # light shield with dark outline + dark cross, left side
    $sx = [math]::Max(0, $BB.x - 8); $sy = $BB.y + 6
    $shield = @(@(0, 7), @(0, 7), @(0, 7), @(0, 7), @(0, 7), @(1, 5), @(2, 3), @(3, 1))
    Draw-RowsOutline $Bmp $sx $sy $shield $DARK 1
    Draw-Rows $Bmp $sx $sy $shield $LIGHT
    Draw-Rows $Bmp ($sx + 2) ($sy + 3) @(@(0, 3), @(0, 3), @(0, 3)) $DARK
    Set-Px $Bmp ($sx + 3) ($sy + 4) $WHITE
    # light axe with dark outline, right side
    $ax = [math]::Min(28, $BB.right + 3); $ay = $BB.y + 7
    for ($y = $ay + 1; $y -le 24; $y++) {
        Set-Px $Bmp ($ax - 1) $y $DARK
        Set-Px $Bmp $ax $y $LIGHT
        Set-Px $Bmp ($ax + 1) $y $DARK
    }
    $head = @(@(3, 2), @(2, 4), @(1, 6), @(1, 6), @(2, 4), @(3, 2))
    Draw-RowsOutline $Bmp ($ax - 2) ($ay - 2) $head $DARK 1
    Draw-Rows $Bmp ($ax - 2) ($ay - 2) $head $LIGHT
    Draw-Line $Bmp ($ax - 1) ($ay + 1) ($ax - 1) ($ay + 4) $DARK
    Set-Px $Bmp ($ax + 1) ($ay - 1) $WHITE
}

function Add-MageProps {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    $cx = 16
    # big orb staff, right side: 7x7 orb + sparkles + crescent holder + 3px shaft
    $sx = [math]::Min(26, $BB.right + 3)
    $oy = [math]::Max(0, $BB.y - 8)
    $orb = @(@(3, 1), @(2, 3), @(1, 5), @(0, 7), @(1, 5), @(2, 3), @(3, 1))
    Draw-RowsOutline $Bmp ($sx - 2) $oy $orb $DARK 1
    Draw-Rows $Bmp ($sx - 2) $oy $orb $LIGHT
    Draw-Rows $Bmp ($sx - 1) ($oy + 3) @(@(0, 2), @(0, 2)) $WHITE
    Set-Px $Bmp ($sx + 1) ($oy - 1) $WHITE
    Set-Px $Bmp ($sx + 1) ($oy + 7) $WHITE
    Set-Px $Bmp ($sx - 3) ($oy + 3) $WHITE
    Set-Px $Bmp ($sx + 5) ($oy + 3) $WHITE
    # crescent holder under the orb
    $cy = $oy + 7
    Set-Px $Bmp ($sx - 3) $cy $DARK
    Set-Px $Bmp ($sx + 5) $cy $DARK
    Set-Px $Bmp ($sx - 2) ($cy + 1) $LIGHT
    Set-Px $Bmp ($sx - 1) ($cy + 1) $LIGHT
    Set-Px $Bmp ($sx + 3) ($cy + 1) $LIGHT
    Set-Px $Bmp ($sx + 4) ($cy + 1) $LIGHT
    # shaft
    for ($y = $cy + 2; $y -le 24; $y++) {
        Set-Px $Bmp $sx $y $DARK
        Set-Px $Bmp ($sx + 1) $y $LIGHT
        Set-Px $Bmp ($sx + 2) $y $DARK
    }
    # wide-brim pointed wizard hat (not a helmet)
    if ($BB.y -ge 6) {
        $cx = 16; $by = $BB.y - 1
        $brim = @(@(0, 13))
        Draw-RowsOutline $Bmp ($cx - 6) $by $brim $DARK 1
        Draw-Rows $Bmp ($cx - 6) $by $brim $LIGHT
        Draw-Rows $Bmp ($cx - 6) ($by + 1) @(@(0, 13)) $DARK
        $cone = @(@(3, 1), @(2, 3), @(1, 5), @(0, 7))
        Draw-RowsOutline $Bmp ($cx - 3) ($by - 4) $cone $DARK 1
        Draw-Rows $Bmp ($cx - 3) ($by - 4) $cone $LIGHT
        Set-Px $Bmp ($cx - 2) ($by - 3) $WHITE
    }
    # ogre mage: shoulder runes as a secondary cue
    if ($BB.w -ge 17) {
        Set-Px $Bmp ($cx - 6) ($BB.y + 7) $WHITE
        Set-Px $Bmp ($cx - 5) ($BB.y + 8) $WHITE
        Set-Px $Bmp ($cx - 4) ($BB.y + 7) $WHITE
    }
}

function Add-RangerProps {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    # light bow with dark edges, string + arrow, left side
    $bx = [math]::Max(0, $BB.x - 7)
    $y0 = [math]::Max(6, $BB.y + 1); $y1 = 24
    for ($y = $y0; $y -le $y1; $y++) {
        $t = ($y - $y0) / [double]($y1 - $y0)
        $bulge = [int][math]::Round(2 * [math]::Sin([math]::PI * $t))
        $x = $bx + (2 - $bulge)
        Set-Px $Bmp ($x - 1) $y $DARK
        Set-Px $Bmp $x $y $LIGHT
        Set-Px $Bmp ($x + 1) $y $DARK
    }
    for ($y = $y0 + 1; $y -lt $y1; $y++) { Set-Px $Bmp ($bx + 4) $y 150 }
    $mid = [int](($y0 + $y1) / 2)
    Draw-Line $Bmp ($bx + 2) $mid ($bx + 7) $mid $LIGHT
    Set-Px $Bmp ($bx + 7) $mid $WHITE
    # light quiver with three dark arrows, right side
    $qx = [math]::Min(27, $BB.right + 1); $qy = $BB.y + 4
    $quiver = @(@(1, 3), @(0, 5), @(0, 5), @(0, 5), @(0, 5), @(0, 5), @(1, 3))
    Draw-RowsOutline $Bmp $qx $qy $quiver $DARK 1
    Draw-Rows $Bmp $qx $qy $quiver $LIGHT
    for ($i = 0; $i -lt 3; $i++) {
        Set-Px $Bmp ($qx + 1 + $i) ($qy - 1) $DARK
        Set-Px $Bmp ($qx + 1 + $i) ($qy - 2) $DARK
        Set-Px $Bmp ($qx + 1 + $i) ($qy - 3) $WHITE
    }
}

function Add-AssassinProps {
    param([System.Drawing.Bitmap]$Bmp, $BB)
    # dark hood above the head (skull/face stays visible)
    $cx = 16; $hy = [math]::Max(0, $BB.y - 4)
    if ($BB.y -ge 5) {
        $hood = @(@(3, 3), @(2, 5), @(1, 7), @(0, 9))
        Draw-Rows $Bmp ($cx - 4) $hy $hood $DARK
        Draw-Rows $Bmp ($cx - 1) $hy @(@(0, 1)) $MID
    }
    # short broad dagger across the torso, big crossguard (not a staff)
    $topX = [math]::Min(28, $BB.right + 1); $botX = [math]::Max(2, $BB.x - 2)
    $y0 = $BB.y + 8; $y1 = [math]::Min(23, $BB.y + 15)
    for ($y = $y0; $y -le $y1; $y++) {
        $t = ($y - $y0) / [double]($y1 - $y0)
        $x = [int][math]::Round($topX + ($botX - $topX) * $t)
        Set-Px $Bmp ($x - 1) $y $DARK
        Set-Px $Bmp $x $y $LIGHT
        Set-Px $Bmp ($x + 1) $y $DARK
    }
    # crossguard + pommel
    for ($i = -2; $i -le 2; $i++) { Set-Px $Bmp ($botX + $i) ($y1 + 1) $DARK }
    Set-Px $Bmp $botX ($y1 + 1) $LIGHT
    Set-Px $Bmp $botX ($y1 + 2) $MID
}

function Add-RaceMarkers {
    param([System.Drawing.Bitmap]$Bmp, $BB, [string]$Race)
    $cx = 16
    switch ($Race) {
        'orc' {
            # keep the source tusks; no overlay (2x2 blocks read as white eyes)
        }
        'wogol' {
            # protruding muzzle with dark nose: a beast cue that survives hats/hoods
            $my = $BB.y + 6
            for ($dy = 0; $dy -le 2; $dy++) { for ($dx = -2; $dx -le 2; $dx++) { Set-Px $Bmp ($cx + $dx) ($my + $dy) $DARK } }
            for ($dy = 0; $dy -le 1; $dy++) { for ($dx = -1; $dx -le 1; $dx++) { Set-Px $Bmp ($cx + $dx) ($my + $dy) $LIGHT } }
            Set-Px $Bmp $cx ($my + 1) $DARK
        }
        'skelet' {
            # jaw line only; keep the skull's original sockets
            Draw-Line $Bmp ($cx - 3) ($BB.y + 8) ($cx + 3) ($BB.y + 8) $DARK
        }
        'ogre' {
            # broad shoulder pauldrons + wide belt (distinct from wogol ears)
            $py = $BB.y + 5
            foreach ($side in @('L', 'R')) {
                if ($side -eq 'L') { $px = [math]::Max(0, $BB.x - 1) } else { $px = [math]::Min(27, $BB.right - 1) }
                for ($dy = 0; $dy -le 1; $dy++) {
                    for ($dx = 0; $dx -le 3; $dx++) { Set-Px $Bmp ($px + $dx) ($py + $dy) $DARK }
                }
                Set-Px $Bmp ($px + 1) $py $LIGHT
                Set-Px $Bmp ($px + 1) ($py + 1) $LIGHT
            }
            # wide belt + buckle
            $by = $BB.y + 15
            for ($x = $cx - 3; $x -le $cx + 3; $x++) { Set-Px $Bmp $x $by $DARK; Set-Px $Bmp $x ($by + 1) $DARK }
            Set-Px $Bmp ($cx - 1) $by $WHITE
            Set-Px $Bmp $cx $by $WHITE
            Set-Px $Bmp ($cx - 1) ($by + 1) $WHITE
            Set-Px $Bmp $cx ($by + 1) $WHITE
        }
    }
}





# ---------- build bodies ----------
$Bodies = @{}
foreach ($u in $UnitDefs) {
    $src = Get-IdleFrame (Join-Path $SrcDir $u.src) $u.fw $u.fh
    $bb = Get-BBox $src
    $crop = [System.Drawing.Bitmap]::new($bb.w, $bb.h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($crop)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.DrawImage($src, [System.Drawing.Rectangle]::new(0, 0, $bb.w, $bb.h), [System.Drawing.Rectangle]::new($bb.x, $bb.y, $bb.w, $bb.h), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    $lut = Get-RankLut $crop
    $grey = Convert-ToGrey $crop $lut
    $scaled = Resize-Nearest $grey ([int][math]::Round($bb.w * $u.targetH / $bb.h)) $u.targetH
    $canvas = Place-OnCanvas $scaled 26
    $Bodies[$u.key] = $canvas
    $src.Dispose(); $crop.Dispose(); $grey.Dispose(); $scaled.Dispose()
}

# ---------- compose 16 ----------
$manifest = New-Object System.Collections.Generic.List[object]
foreach ($u in $UnitDefs) {
    foreach ($cls in $ClassDefs) {
        $base = $Bodies[$u.key]
        $bmp = [System.Drawing.Bitmap]::new($base)
        if ($cls.key -eq 'assassin') {
            $sq = Squash-Bottom $bmp 0.85
            $bmp.Dispose(); $bmp = $sq
        }
        $bb = Get-BBox $bmp
        switch ($cls.key) {
            'warrior'  { Add-WarriorProps $bmp $bb }
            'mage'     { Add-MageProps $bmp $bb }
            'ranger'   { Add-RangerProps $bmp $bb }
            'assassin' { Add-AssassinProps $bmp $bb }
        }
        Add-RaceMarkers $bmp $bb $u.key
        $key = '{0}_{1}' -f $u.key, $cls.key
        $file = $key + '.png'
        $path = Join-Path $UnitDir $file
        $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
        $bottom = -1; $colors = @{}
        for ($y = 31; $y -ge 0; $y--) { for ($x = 0; $x -lt 32; $x++) { $c = $bmp.GetPixel($x, $y); if ($c.A -gt 0) { if ($bottom -lt 0) { $bottom = $y }; $colors['{0},{1},{2}' -f $c.R, $c.G, $c.B] = 1 } } }
        $bb2 = Get-BBox $bmp
        $manifest.Add([pscustomobject]@{
            key = $key; file = 'units/' + $file; race = $u.race; body = $u.key; class = $cls.name
            width = 32; height = 32; bottom_row = $bottom; colors = $colors.Count
            bbox_x = $bb2.x; bbox_w = $bb2.w; bbox_y = $bb2.y; bbox_h = $bb2.h
            sha256 = (Get-FileHash -Algorithm SHA256 $path).Hash.ToLower()
        })
        $bmp.Dispose()
    }
}

$csv = $manifest | ConvertTo-Csv -NoTypeInformation
[IO.File]::WriteAllText((Join-Path $OutDir 'manifest.csv'), ($csv -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($false)))

# ---------- previews ----------
$Font = [System.Drawing.Font]::new('Consolas', 10)
$FontSm = [System.Drawing.Font]::new('Consolas', 8)

function New-Sheet {
    param([int]$W, [int]$H, [int]$BgR = 22, [int]$BgG = 22, [int]$BgB = 28)
    $bmp = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb($BgR, $BgG, $BgB))
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    return @{ bmp = $bmp; g = $g }
}

# A. labeled 32px grid (4x)
$cell = 32 * 4; $gut = 90; $hdr = 22; $gap = 8
$shA = New-Sheet ($gut + 4 * ($cell + $gap) + 10) ($hdr + 4 * ($cell + $gap) + 10)
$g = $shA.g
for ($c = 0; $c -lt 4; $c++) { $g.DrawString($ClassDefs[$c].label, $Font, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), 3) }
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($UnitDefs[$r].label, $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    for ($c = 0; $c -lt 4; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ('{0}_{1}.png' -f $UnitDefs[$r].key, $ClassDefs[$c].key)))
        $g.DrawImage($b, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
        $b.Dispose()
    }
}
$g.Dispose(); $shA.bmp.Save((Join-Path $PrevDir 'grid_32_labeled.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shA.bmp.Dispose()

# B. labeled 24px grid (8x)
$cell = 24 * 8; $gut = 90; $hdr = 22; $gap = 10
$shB = New-Sheet ($gut + 4 * ($cell + $gap) + 10) ($hdr + 4 * ($cell + $gap) + 10)
$g = $shB.g
for ($c = 0; $c -lt 4; $c++) { $g.DrawString($ClassDefs[$c].label, $Font, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), 3) }
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($UnitDefs[$r].label, $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    for ($c = 0; $c -lt 4; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ('{0}_{1}.png' -f $UnitDefs[$r].key, $ClassDefs[$c].key)))
        $s = Resize-Nearest $b 24 24
        $g.DrawImage($s, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
        $b.Dispose(); $s.Dispose()
    }
}
$g.Dispose(); $shB.bmp.Save((Join-Path $PrevDir 'grid_24_labeled.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shB.bmp.Dispose()

# C. blind 24px grid: Latin-square arrangement, numbered, no race/class labels
$order = @(
    @(0, 0), @(1, 1), @(2, 2), @(3, 3),
    @(1, 0), @(2, 1), @(3, 2), @(0, 3),
    @(2, 0), @(3, 1), @(0, 2), @(1, 3),
    @(3, 0), @(0, 1), @(1, 2), @(2, 3)
)
$cell = 24 * 8; $gap = 10; $hdr = 10
$shC = New-Sheet (4 * ($cell + $gap) + 10) (4 * ($cell + $gap) + 10) 22 22 28
$g = $shC.g
for ($i = 0; $i -lt 16; $i++) {
    $r = [int][math]::Floor($i / 4); $c = $i % 4
    $ui = $order[$i][0]; $ci = $order[$i][1]
    $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ('{0}_{1}.png' -f $UnitDefs[$ui].key, $ClassDefs[$ci].key)))
    $s = Resize-Nearest $b 24 24
    $x = 5 + $c * ($cell + $gap); $y = 5 + $r * ($cell + $gap)
    $g.DrawImage($s, $x, $y, $cell, $cell)
    $g.DrawString(('{0:00}' -f ($i + 1)), $FontSm, [System.Drawing.Brushes]::LightGray, ($x + 2), ($y + 2))
    $b.Dispose(); $s.Dispose()
}
$g.Dispose(); $shC.bmp.Save((Join-Path $PrevDir 'blind_24_unlabeled.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shC.bmp.Dispose()

# blind key
$lines = New-Object System.Collections.Generic.List[string]
$lines.Add('# 盲测答案键（24px 版）')
$lines.Add('')
$lines.Add('| 位置 | 种族身体 | 职业 |')
$lines.Add('|---|---|---|')
for ($i = 0; $i -lt 16; $i++) {
    $ui = $order[$i][0]; $ci = $order[$i][1]
    $lines.Add(('| {0:00} | {1} | {2} |' -f ($i + 1), $UnitDefs[$ui].label, $ClassDefs[$ci].name))
}
[IO.File]::WriteAllLines((Join-Path $OutDir 'BLIND_KEY.md'), $lines, (New-Object Text.UTF8Encoding($false)))
# D. 24px 3-star tint grid (labeled) — verifies props survive the star tint
$cell = 24 * 8; $gut = 90; $hdr = 22; $gap = 10
$shT = New-Sheet ($gut + 4 * ($cell + $gap) + 10) ($hdr + 4 * ($cell + $gap) + 10)
$g = $shT.g
for ($c = 0; $c -lt 4; $c++) { $g.DrawString($ClassDefs[$c].label, $Font, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), 3) }
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($UnitDefs[$r].label, $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    for ($c = 0; $c -lt 4; $c++) {
        $b = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ('{0}_{1}.png' -f $UnitDefs[$r].key, $ClassDefs[$c].key)))
        $s = Resize-Nearest $b 24 24
        $t = [System.Drawing.Bitmap]::new(24, 24, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        for ($y = 0; $y -lt 24; $y++) {
            for ($x = 0; $x -lt 24; $x++) {
                $px = $s.GetPixel($x, $y)
                if ($px.A -eq 0) { continue }
                $t.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($px.A,
                    [int][math]::Round($px.R * 0.72), [int][math]::Round($px.G * 0.40), [int][math]::Round($px.B * 0.96)))
            }
        }
        $g.DrawImage($t, ($gut + $c * ($cell + $gap)), ($hdr + $r * ($cell + $gap)), $cell, $cell)
        $b.Dispose(); $s.Dispose(); $t.Dispose()
    }
}
$g.Dispose(); $shT.bmp.Save((Join-Path $PrevDir 'grid_24_3star.png'), [System.Drawing.Imaging.ImageFormat]::Png); $shT.bmp.Dispose()



$bad = @($manifest | Where-Object { $_.bottom_row -ne 26 }).Count
Write-Output ("OK cells={0} baseline_bad={1} max_colors={2}" -f $manifest.Count, $bad, (($manifest | Measure-Object colors -Maximum).Maximum))
Write-Output ("OUT " + $OutDir)
