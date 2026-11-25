# Pilot sprite pipeline: 0x72 DungeonTileset II -> 32x32 star-tint-ready frames
# Requires Windows PowerShell 5.1 + System.Drawing. Deterministic (no RNG).
# Run from anywhere: powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\process_pilot.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SrcDir = Join-Path $Root 'assets_src\sourced\0x72-dungeontileset-ii'
$OutDir = Join-Path $Root 'assets_src\draft_batch1'
$UnitDir = Join-Path $OutDir 'units'
$ColorDir = Join-Path $OutDir 'variants_color'
$PrevDir = Join-Path $OutDir 'previews'
New-Item -ItemType Directory -Force -Path $UnitDir, $ColorDir, $PrevDir | Out-Null

$Pack = @{
    name      = '0x72 DungeonTileset II'
    author    = '0x72 (Robert)'
    url       = 'https://0x72.itch.io/dungeontileset-ii'
    mirror    = 'https://hydecogames.com/art/packs/0x72-dungeontileset-ii/'
    license   = 'CC0 1.0 (public domain)'
    licenseUrl = 'https://creativecommons.org/publicdomain/zero/1.0/'
    attribution = 'No credit required (CC0). Recorded for provenance: 0x72 (Robert), DungeonTileset II.'
}

# ---- low-level image helpers -------------------------------------------------

function Get-Frame {
    param([System.Drawing.Bitmap]$Sheet, [int]$Index, [int]$Fw, [int]$Fh)
    $bmp = [System.Drawing.Bitmap]::new($Fw, $Fh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $srcRect = [System.Drawing.Rectangle]::new($Index * $Fw, 0, $Fw, $Fh)
    $dstRect = [System.Drawing.Rectangle]::new(0, 0, $Fw, $Fh)
    $g.DrawImage($Sheet, $dstRect, $srcRect, [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return $bmp
}

function Get-AlphaBBox {
    param([System.Drawing.Bitmap]$Bmp)
    $minX = 9999; $minY = 9999; $maxX = -1; $maxY = -1
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            if ($Bmp.GetPixel($x, $y).A -gt 0) {
                if ($x -lt $minX) { $minX = $x }
                if ($x -gt $maxX) { $maxX = $x }
                if ($y -lt $minY) { $minY = $y }
                if ($y -gt $maxY) { $maxY = $y }
            }
        }
    }
    if ($maxX -lt 0) { throw 'empty frame (no opaque pixels)' }
    return @{ x = $minX; y = $minY; w = ($maxX - $minX + 1); h = ($maxY - $minY + 1) }
}

function Get-RankLut {
    # unique opaque RGB across frames -> luminance rank -> grey ramp
    param([System.Drawing.Bitmap[]]$Frames)
    $lum = @{}
    foreach ($bmp in $Frames) {
        for ($y = 0; $y -lt $bmp.Height; $y++) {
            for ($x = 0; $x -lt $bmp.Width; $x++) {
                $c = $bmp.GetPixel($x, $y)
                if ($c.A -eq 0) { continue }
                $k = '{0},{1},{2}' -f $c.R, $c.G, $c.B
                if (-not $lum.ContainsKey($k)) {
                    $lum[$k] = 0.299 * $c.R + 0.587 * $c.G + 0.114 * $c.B
                }
            }
        }
    }
    $sorted = @($lum.GetEnumerator() | Sort-Object Value)
    $n = $sorted.Count
    $lut = @{}
    for ($i = 0; $i -lt $n; $i++) {
        $t = if ($n -le 1) { 1.0 } else { $i / ($n - 1) }
        $v = [int][math]::Round((0.12 + 0.85 * [math]::Pow($t, 0.70)) * 255)
        if ($v -gt 255) { $v = 255 }
        $lut[$sorted[$i].Key] = $v
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
    if ($Dw -lt 1) { $Dw = 1 }
    if ($Dh -lt 1) { $Dh = 1 }
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

function Adjust-Value {
    param([System.Drawing.Bitmap]$Bmp, [double]$Factor)
    $dst = [System.Drawing.Bitmap]::new($Bmp.Width, $Bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $v = [int][math]::Min(255, [math]::Round($c.R * $Factor))
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, $v, $v, $v))
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
        $ty = $y + $sy
        if ($ty -lt 0 -or $ty -gt 31) { continue }
        for ($sx = 0; $sx -lt $Bmp.Width; $sx++) {
            $tx = $x + $sx
            if ($tx -lt 0 -or $tx -gt 31) { continue }
            $c = $Bmp.GetPixel($sx, $sy)
            if ($c.A -gt 0) { $canvas.SetPixel($tx, $ty, $c) }
        }
    }
    return $canvas
}

function Add-Rim {
    # 1px bright rim on transparent pixels adjacent to opaque ones (never below baseline row 26)
    param([System.Drawing.Bitmap]$Bmp, [int]$MaxY = 26)
    $copy = [System.Drawing.Bitmap]::new($Bmp.Width, $Bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt 32; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            $copy.SetPixel($x, $y, $Bmp.GetPixel($x, $y))
        }
    }
    for ($y = 0; $y -le $MaxY; $y++) {
        for ($x = 0; $x -lt 32; $x++) {
            if ($Bmp.GetPixel($x, $y).A -gt 0) { continue }
            $hit = $false
            foreach ($d in @(@(1, 0), @(-1, 0), @(0, 1), @(0, -1))) {
                $nx = $x + $d[0]; $ny = $y + $d[1]
                if ($nx -ge 0 -and $nx -lt 32 -and $ny -ge 0 -and $ny -lt 32 -and $Bmp.GetPixel($nx, $ny).A -gt 0) { $hit = $true; break }
            }
            if ($hit) { $copy.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, 255, 255, 255)) }
        }
    }
    return $copy
}

function Add-Lean {
    # pixel-art lean: top rows shift horizontally by Px, bottom row stays put
    param([System.Drawing.Bitmap]$Bmp, [int]$Px)
    $dst = [System.Drawing.Bitmap]::new($Bmp.Width, $Bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $h = $Bmp.Height
    for ($y = 0; $y -lt $h; $y++) {
        $shift = [int][math]::Round($Px * ($h - 1 - $y) / [double]($h - 1))
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $tx = $x + $shift
            if ($tx -ge 0 -and $tx -lt $Bmp.Width) { $dst.SetPixel($tx, $y, $c) }
        }
    }
    return $dst
}

function Get-BottomRow {
    param([System.Drawing.Bitmap]$Bmp)
    for ($y = $Bmp.Height - 1; $y -ge 0; $y--) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            if ($Bmp.GetPixel($x, $y).A -gt 0) { return $y }
        }
    }
    return -1
}

function Get-ColorCount {
    param([System.Drawing.Bitmap]$Bmp)
    $set = @{}
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $set['{0},{1},{2}' -f $c.R, $c.G, $c.B] = 1
        }
    }
    return $set.Count
}

# ---- preview helpers --------------------------------------------------------

function New-Sheet {
    param([int]$W, [int]$H, [int]$BgR = 18, [int]$BgG = 18, [int]$BgB = 24)
    $bmp = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.Clear([System.Drawing.Color]::FromArgb($BgR, $BgG, $BgB))
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    return @{ bmp = $bmp; g = $g }
}

function Draw-Scaled {
    param($G, [System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$Scale)
    $G.DrawImage($Bmp, $X, $Y, $Bmp.Width * $Scale, $Bmp.Height * $Scale)
}

function Get-Tinted {
    param([System.Drawing.Bitmap]$Bmp, [double]$R, [double]$G, [double]$B)
    $dst = [System.Drawing.Bitmap]::new($Bmp.Width, $Bmp.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $Bmp.Height; $y++) {
        for ($x = 0; $x -lt $Bmp.Width; $x++) {
            $c = $Bmp.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A,
                [int][math]::Round($c.R * $R), [int][math]::Round($c.G * $G), [int][math]::Round($c.B * $B)))
        }
    }
    return $dst
}

function Flip-X {
    param([System.Drawing.Bitmap]$Bmp)
    $copy = [System.Drawing.Bitmap]::new($Bmp)
    $copy.RotateFlip([System.Drawing.RotateFlipType]::RotateNoneFlipX)
    return $copy
}

# ---- unit definitions ------------------------------------------------------

$UnitDefs = @(
    @{ id = 'unit_warrior_01';       race = '兽人'; cls = '战士'; base = 'monsters\orc_warrior'; fw = 16; fh = 20; targetH = 21 },
    @{ id = 'unit_wolf_pup';         race = '野兽'; cls = '刺客'; base = 'monsters\wogol';       fw = 16; fh = 20; targetH = 19 },
    @{ id = 'unit_skeleton_soldier'; race = '亡灵'; cls = '战士'; base = 'monsters\skelet';      fw = 16; fh = 16; targetH = 20 },
    @{ id = 'unit_frost_giant';      race = '巨人'; cls = '战士'; base = 'monsters\ogre';        fw = 32; fh = 32; targetH = 25 }
)

# 12 output frames per unit: (anim, frame, source sheet, source index, transform)
function Get-FramePlan {
    return @(
        @{ anim = 'idle';   frame = 0; sheet = 'idle'; idx = 0; op = 'plain' },
        @{ anim = 'idle';   frame = 1; sheet = 'idle'; idx = 1; op = 'plain' },
        @{ anim = 'walk';   frame = 0; sheet = 'run';  idx = 1; op = 'plain' },
        @{ anim = 'walk';   frame = 1; sheet = 'run';  idx = 3; op = 'plain' },
        @{ anim = 'attack'; frame = 0; sheet = 'idle'; idx = 0; op = 'lean'; lean = -1; value = 0.92 },
        @{ anim = 'attack'; frame = 1; sheet = 'run';  idx = 2; op = 'lean'; lean = 2;  value = 1.06 },
        @{ anim = 'attack'; frame = 2; sheet = 'idle'; idx = 1; op = 'lean'; lean = 1;  value = 1.00 },
        @{ anim = 'cast';   frame = 0; sheet = 'idle'; idx = 0; op = 'plain' },
        @{ anim = 'cast';   frame = 1; sheet = 'idle'; idx = 0; op = 'rim'; value = 1.06 },
        @{ anim = 'death';  frame = 0; sheet = 'idle'; idx = 0; op = 'plain' },
        @{ anim = 'death';  frame = 1; sheet = 'idle'; idx = 0; op = 'fall'; lean = 2; value = 0.85 },
        @{ anim = 'death';  frame = 2; sheet = 'idle'; idx = 0; op = 'rotate'; value = 0.75 }
    )
}

$manifest = New-Object System.Collections.Generic.List[object]

foreach ($u in $UnitDefs) {
    $idleSheet = [System.Drawing.Bitmap]::new((Join-Path $SrcDir ($u.base + '_idle_anim.png')))
    $runSheet = [System.Drawing.Bitmap]::new((Join-Path $SrcDir ($u.base + '_run_anim.png')))
    $idleFrames = @()
    $runFrames = @()
    for ($i = 0; $i -lt [int]($idleSheet.Width / $u.fw); $i++) { $idleFrames += Get-Frame $idleSheet $i $u.fw $u.fh }
    for ($i = 0; $i -lt [int]($runSheet.Width / $u.fw); $i++) { $runFrames += Get-Frame $runSheet $i $u.fw $u.fh }
    $idleSheet.Dispose(); $runSheet.Dispose()

    $all = @($idleFrames) + @($runFrames)
    $lut = Get-RankLut $all

    foreach ($p in (Get-FramePlan)) {
        $srcFrames = if ($p.sheet -eq 'idle') { $idleFrames } else { $runFrames }
        $src = $srcFrames[$p.idx]
        $bb = Get-AlphaBBox $src

        # crop content
        $crop = [System.Drawing.Bitmap]::new($bb.w, $bb.h, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
        $gc = [System.Drawing.Graphics]::FromImage($crop)
        $gc.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
        $gc.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
        $gc.DrawImage($src, [System.Drawing.Rectangle]::new(0, 0, $bb.w, $bb.h), [System.Drawing.Rectangle]::new($bb.x, $bb.y, $bb.w, $bb.h), [System.Drawing.GraphicsUnit]::Pixel)
        $gc.Dispose()

        $scale = $u.targetH / $bb.h
        $dh = [int]$u.targetH
        $dw = [int][math]::Round($bb.w * $scale)

        if ($p.anim -eq 'idle' -and $p.frame -eq 0) {
            $colorScaled = Resize-Nearest $crop $dw $dh
            $colorCanvas = Place-OnCanvas $colorScaled 26
            $colorCanvas.Save((Join-Path $ColorDir ('{0}_idle_0.png' -f $u.id)), [System.Drawing.Imaging.ImageFormat]::Png)
            $colorCanvas.Dispose(); $colorScaled.Dispose()
        }
        $grey = Convert-ToGrey $crop $lut
        $crop.Dispose()

        $lean = if ($p.ContainsKey('lean')) { [int]$p.lean } else { 0 }
        $value = if ($p.ContainsKey('value')) { [double]$p.value } else { 1.0 }

        if ($p.op -eq 'squash' -or $p.op -eq 'fall') {
            $dh = [int][math]::Max(4, [math]::Round($dh * 0.62))
            $scaled = Resize-Nearest $grey $dw $dh
        } elseif ($p.op -eq 'rotate') {
            $scaled = Resize-Nearest $grey $dw $dh
            $scaled.RotateFlip([System.Drawing.RotateFlipType]::Rotate90FlipNone)
        } else {
            $scaled = Resize-Nearest $grey $dw $dh
        }
        $grey.Dispose()

        if ($lean -ne 0) {
            $leaned = Add-Lean $scaled $lean
            $scaled.Dispose()
            $scaled = $leaned
        }
        if ($value -ne 1.0) {
            $adjusted = Adjust-Value $scaled $value
            $scaled.Dispose()
            $scaled = $adjusted
        }

        $canvas = Place-OnCanvas $scaled 26
        $scaled.Dispose()
        if ($p.op -eq 'rim') {
            $rimmed = Add-Rim $canvas 26
            $canvas.Dispose()
            $canvas = $rimmed
        }

        $bottom = Get-BottomRow $canvas
        if ($bottom -ne 26) { throw "baseline broken: $($u.id) $($p.anim)_$($p.frame) bottom=$bottom" }

        $key = '{0}_{1}_{2}' -f $u.id, $p.anim, $p.frame
        $file = $key + '.png'
        $outPath = Join-Path $UnitDir $file
        $canvas.Save($outPath, [System.Drawing.Imaging.ImageFormat]::Png)
        $canvas.Dispose()

        $manifest.Add([pscustomobject]@{
            key            = $key
            file           = 'units/' + $file
            width          = 32
            height         = 32
            sha256         = (Get-FileHash -Algorithm SHA256 $outPath).Hash.ToLower()
            unit_id        = $u.id
            race           = $u.race
            class          = $u.cls
            source_pack    = $Pack.name
            source_url     = $Pack.url
            author         = $Pack.author
            license        = $Pack.license
            attribution    = $Pack.attribution
            source_sheet   = ($u.base -replace '\\', '/') + '_' + $p.sheet + '_anim.png'
            source_frame   = $p.idx
            transform      = $p.op
            bottom_row     = $bottom
            palette_colors = (Get-ColorCount ([System.Drawing.Bitmap]::new($outPath)))
        })
    }

    foreach ($f in $all) { $f.Dispose() }
}

# ---- manifest --------------------------------------------------------------

$csv = $manifest | ConvertTo-Csv -NoTypeInformation
[IO.File]::WriteAllText((Join-Path $OutDir 'manifest.csv'), ($csv -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($false)))
[IO.File]::WriteAllText((Join-Path $OutDir 'manifest.json'), ($manifest | ConvertTo-Json -Depth 4), (New-Object Text.UTF8Encoding($false)))

# ---- previews --------------------------------------------------------------

$units = @($UnitDefs | ForEach-Object { $_.id })
$Font = [System.Drawing.Font]::new('Consolas', 10)
$FontSm = [System.Drawing.Font]::new('Consolas', 8)
$anims = @('idle', 'idle', 'walk', 'walk', 'attack', 'attack', 'attack', 'cast', 'cast', 'death', 'death', 'death')
$frames = @(0, 1, 0, 1, 0, 1, 2, 0, 1, 0, 1, 2)

# A. contact sheet: 4 rows x 12 frames, 3x, with row/column headers
$cell = 32 * 3; $gap = 6; $hdr = 22; $gut = 150
$A = New-Sheet ($gut + 12 * ($cell + $gap) + 8) ($hdr + 4 * ($cell + $gap) + 8)
$g = $A.g
for ($c = 0; $c -lt 12; $c++) {
    $g.DrawString(('{0}_{1}' -f $anims[$c], $frames[$c]), $FontSm, [System.Drawing.Brushes]::LightGray, ($gut + $c * ($cell + $gap) + 2), 5)
}
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($units[$r], $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 7))
    for ($c = 0; $c -lt 12; $c++) {
        $key = '{0}_{1}_{2}' -f $units[$r], $anims[$c], $frames[$c]
        $bmp = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($key + '.png')))
        Draw-Scaled $g $bmp ($gut + $c * ($cell + $gap)) ($hdr + $r * ($cell + $gap)) 3
        $bmp.Dispose()
    }
}
$g.Dispose(); $A.bmp.Save((Join-Path $PrevDir 'contact_all.png'), [System.Drawing.Imaging.ImageFormat]::Png); $A.bmp.Dispose()

# B. star tint triptych (idle_0) on buffer-zone background
$cell = 32 * 4; $gap = 10; $hdr = 22; $gut = 150
$B = New-Sheet ($gut + 3 * ($cell + $gap) + 8) ($hdr + 4 * ($cell + $gap) + 8) 30 28 24
$g = $B.g
$tints = @(@(0.82, 0.82, 0.78, '1star'), @(0.98, 0.78, 0.22, '2star'), @(0.72, 0.40, 0.96, '3star'))
for ($c = 0; $c -lt 3; $c++) {
    $g.DrawString($tints[$c][3], $Font, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 4), 4)
}
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($units[$r], $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 7))
    $bmp = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($units[$r] + '_idle_0.png')))
    for ($c = 0; $c -lt 3; $c++) {
        $t = Get-Tinted $bmp $tints[$c][0] $tints[$c][1] $tints[$c][2]
        Draw-Scaled $g $t ($gut + $c * ($cell + $gap)) ($hdr + $r * ($cell + $gap)) 4
        $t.Dispose()
    }
    $bmp.Dispose()
}
$g.Dispose(); $B.bmp.Save((Join-Path $PrevDir 'star_tints.png'), [System.Drawing.Imaging.ImageFormat]::Png); $B.bmp.Dispose()

# C. 24px simulated display vs 32px reference (8x view)
$cell = 32 * 8; $gap = 12; $hdr = 22; $gut = 150
$C = New-Sheet ($gut + 2 * ($cell + $gap) + 190) ($hdr + 4 * ($cell + $gap) + 8)
$g = $C.g
$g.DrawString('32px reference', $Font, [System.Drawing.Brushes]::White, ($gut + 4), 4)
$g.DrawString('24px nearest (approx. in-game size)', $Font, [System.Drawing.Brushes]::White, ($gut + $cell + $gap + 4), 4)
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($units[$r], $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 7))
    $bmp = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($units[$r] + '_idle_0.png')))
    $small = Resize-Nearest $bmp 24 24
    Draw-Scaled $g $bmp ($gut) ($hdr + $r * ($cell + $gap)) 8
    $g.DrawImage($small, ($gut + $cell + $gap), ($hdr + $r * ($cell + $gap)), 24 * 8, 24 * 8)
    $small.Dispose(); $bmp.Dispose()
}
$g.Dispose(); $C.bmp.Save((Join-Path $PrevDir 'display_24px_vs_32px.png'), [System.Drawing.Imaging.ImageFormat]::Png); $C.bmp.Dispose()

# D. enemy red border + flipX simulation vs player blue border
$cell = 32 * 6; $gap = 12; $hdr = 22; $gut = 180
$D = New-Sheet ($gut + 2 * ($cell + $gap) + 8) ($hdr + 4 * ($cell + $gap) + 8) 44 26 26
$g = $D.g
$g.DrawString('player blue frame', $Font, [System.Drawing.Brushes]::White, ($gut + 4), 4)
$g.DrawString('enemy red frame + flipX', $Font, [System.Drawing.Brushes]::White, ($gut + $cell + $gap + 4), 4)
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($units[$r], $Font, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 7))
    $bmp = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($units[$r] + '_idle_0.png')))
    $flip = Flip-X $bmp
    $x = $gut; $y = $hdr + $r * ($cell + $gap)
    $g.FillRectangle([System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(66, 36, 36)), $x, $y, $cell, $cell)
    $g.DrawImage($bmp, $x, $y, $cell, $cell)
    $pen = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(64, 153, 255), 12)
    $g.DrawRectangle($pen, $x + 6, $y + 6, $cell - 12, $cell - 12); $pen.Dispose()
    $x2 = $gut + $cell + $gap
    $g.FillRectangle([System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(66, 36, 36)), $x2, $y, $cell, $cell)
    $g.DrawImage($flip, $x2, $y, $cell, $cell)
    $pen2 = [System.Drawing.Pen]::new([System.Drawing.Color]::FromArgb(255, 77, 64), 12)
    $g.DrawRectangle($pen2, $x2 + 6, $y + 6, $cell - 12, $cell - 12); $pen2.Dispose()
    $flip.Dispose(); $bmp.Dispose()
}
$g.Dispose(); $D.bmp.Save((Join-Path $PrevDir 'enemy_redframe_flipx.png'), [System.Drawing.Imaging.ImageFormat]::Png); $D.bmp.Dispose()



# E. colour-source vs grey-baked, both under the three star tints (tint-route evidence)
$cell = 32 * 4; $gap = 8; $hdr = 22; $gut = 150
$E = New-Sheet ($gut + 6 * ($cell + $gap) + 8) ($hdr + 4 * ($cell + $gap) + 8) 30 28 24
$g = $E.g
$heads = @('colour 1star', 'colour 2star', 'colour 3star', 'grey 1star', 'grey 2star', 'grey 3star')
for ($c = 0; $c -lt 6; $c++) {
    $g.DrawString($heads[$c], $FontSm, [System.Drawing.Brushes]::White, ($gut + $c * ($cell + $gap) + 2), 5)
}
for ($r = 0; $r -lt 4; $r++) {
    $g.DrawString($units[$r], $FontSm, [System.Drawing.Brushes]::White, 4, ($hdr + $r * ($cell + $gap) + [int]($cell / 2) - 6))
    $color = [System.Drawing.Bitmap]::new((Join-Path $ColorDir ($units[$r] + '_idle_0.png')))
    $grey = [System.Drawing.Bitmap]::new((Join-Path $UnitDir ($units[$r] + '_idle_0.png')))
    for ($c = 0; $c -lt 3; $c++) {
        $t = Get-Tinted $color $tints[$c][0] $tints[$c][1] $tints[$c][2]
        Draw-Scaled $g $t ($gut + $c * ($cell + $gap)) ($hdr + $r * ($cell + $gap)) 4
        $t.Dispose()
        $t2 = Get-Tinted $grey $tints[$c][0] $tints[$c][1] $tints[$c][2]
        Draw-Scaled $g $t2 ($gut + (3 + $c) * ($cell + $gap)) ($hdr + $r * ($cell + $gap)) 4
        $t2.Dispose()
    }
    $color.Dispose(); $grey.Dispose()
}
$g.Dispose(); $E.bmp.Save((Join-Path $PrevDir 'color_vs_grey_stars.png'), [System.Drawing.Imaging.ImageFormat]::Png); $E.bmp.Dispose()

# ---- summary ---------------------------------------------------------------

$rows = $manifest.Count
$bad = @($manifest | Where-Object { $_.bottom_row -ne 26 }).Count
$maxColors = ($manifest | Measure-Object -Property palette_colors -Maximum).Maximum
Write-Output ("OK units={0} frames={1} baseline_bad={2} max_colors={3}" -f $UnitDefs.Count, $rows, $bad, $maxColors)
Write-Output ("OUT " + $OutDir)
