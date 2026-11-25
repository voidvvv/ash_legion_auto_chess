# Golden UI -> 12 UI drawer assets (1x + 1.25x), dark dungeon palette.
# Run: powershell -NoProfile -ExecutionPolicy Bypass -File assets_src\tools\process_ui_assets.ps1
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$SrcDir = Join-Path $Root 'assets_src\sourced\golden-ui'
$Out1x = Join-Path $Root 'assets\art\ui'
$Out125 = Join-Path $Root 'assets\art\ui\1_25x'
$CopyDir = Join-Path $Root 'assets_src\ui_redesign\sourced_assets'
$PrevDir = Join-Path $Root 'assets_src\ui_redesign\previews'
New-Item -ItemType Directory -Force -Path $Out1x, $Out125, $CopyDir, $PrevDir | Out-Null

$Src = [System.Drawing.Bitmap]::new((Join-Path $SrcDir 'ui_split.png'))

# ---------- palette ----------
$OUT   = @(12,10,14)
$BG0   = @(22,26,34)
$BG1   = @(32,38,48)
$BG2   = @(45,52,63)
$BG3   = @(62,72,86)
$WOOD0 = @(56,40,28)
$WOOD1 = @(86,62,42)
$GOLD0 = @(118,84,40)
$GOLD1 = @(158,118,54)
$GOLD2 = @(198,158,86)
$RED0  = @(96,34,34)
$RED1  = @(140,54,46)
$BONE  = @(214,208,190)
$MOSS  = @(70,92,58)
$STEEL = @(86,96,110)

$Map = @{
    '20,12,28'   = $OUT
    '48,52,109'  = $BG0
    '68,36,52'   = $WOOD0
    '78,74,78'   = $BG2
    '210,125,44' = $GOLD1
    '133,76,48'  = $WOOD1
    '222,238,214'= $BONE
    '218,212,94' = $GOLD2
    '117,113,97' = $STEEL
    '208,70,72'  = $RED1
    '109,170,44' = $MOSS
    '89,125,206' = $BG3
    '52,101,36'  = @(40,56,38)
    '133,149,161'= $STEEL
    '210,170,153'= $WOOD1
    '109,194,202'= $STEEL
}
$GoldMap = $Map.Clone()
$GoldMap['78,74,78'] = $GOLD0
$GoldMap['68,36,52'] = $GOLD1
$GoldMap['117,113,97'] = $GOLD2
$GoldMap['48,52,109'] = $WOOD0

# ---------- helpers ----------
function Crop-Bmp {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$W, [int]$H)
    $dstBmp = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($dstBmp)
    $g.CompositingMode = [System.Drawing.Drawing2D.CompositingMode]::SourceCopy
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    $g.DrawImage($Bmp, [System.Drawing.Rectangle]::new(0, 0, $W, $H), [System.Drawing.Rectangle]::new($X, $Y, $W, $H), [System.Drawing.GraphicsUnit]::Pixel)
    $g.Dispose()
    return $dstBmp
}

function Recolor-Bmp {
    param([System.Drawing.Bitmap]$B, [hashtable]$MapTable = $Map, [double]$Shift = 1.0)
    $dst = [System.Drawing.Bitmap]::new($B.Width, $B.Height, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $B.Height; $y++) {
        for ($x = 0; $x -lt $B.Width; $x++) {
            $c = $B.GetPixel($x, $y)
            if ($c.A -eq 0) { continue }
            $k = '{0},{1},{2}' -f $c.R, $c.G, $c.B
            $t = if ($MapTable.ContainsKey($k)) { $MapTable[$k] } else { $Map[$k] }
            if ($null -eq $t) { $t = $STEEL }
            $nr = [int][math]::Min(255, [math]::Round($t[0] * $Shift))
            $ng = [int][math]::Min(255, [math]::Round($t[1] * $Shift))
            $nb = [int][math]::Min(255, [math]::Round($t[2] * $Shift))
            $dst.SetPixel($x, $y, [System.Drawing.Color]::FromArgb($c.A, $nr, $ng, $nb))
        }
    }
    return $dst
}

function Build-NineSlice {
    param([System.Drawing.Bitmap]$S, [int]$Sx, [int]$Sy, [int]$Sw, [int]$Sh,
          [int]$L, [int]$R, [int]$T, [int]$B, [int]$Dw, [int]$Dh)
    $dst = [System.Drawing.Bitmap]::new($Dw, $Dh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $cw = [math]::Max(1, $Sw - $L - $R)
    $ch = [math]::Max(1, $Sh - $T - $B)
    for ($y = 0; $y -lt $Dh; $y++) {
        if ($y -lt $T) { $sy2 = $Sy + $y }
        elseif ($y -ge $Dh - $B) { $sy2 = $Sy + $Sh - ($Dh - $y) }
        else { $sy2 = $Sy + $T + (($y - $T) % $ch) }
        for ($x = 0; $x -lt $Dw; $x++) {
            if ($x -lt $L) { $sx2 = $Sx + $x }
            elseif ($x -ge $Dw - $R) { $sx2 = $Sx + $Sw - ($Dw - $x) }
            else { $sx2 = $Sx + $L + (($x - $L) % $cw) }
            $c = $S.GetPixel($sx2, $sy2)
            if ($c.A -gt 0) { $dst.SetPixel($x, $y, $c) }
        }
    }
    return $dst
}

function Clear-BitmapCenter {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$W, [int]$H)
    $dst = [System.Drawing.Bitmap]::new($Bmp)
    for ($yy = $Y; $yy -lt $Y + $H; $yy++) {
        for ($xx = $X; $xx -lt $X + $W; $xx++) {
            if ($xx -ge 0 -and $xx -lt $dst.Width -and $yy -ge 0 -and $yy -lt $dst.Height) {
                $dst.SetPixel($xx, $yy, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            }
        }
    }
    return $dst
}

function Blit-Part {
    param([System.Drawing.Bitmap]$Dst, [System.Drawing.Bitmap]$Part, [int]$X, [int]$Y)
    for ($yy = 0; $yy -lt $Part.Height; $yy++) {
        for ($xx = 0; $xx -lt $Part.Width; $xx++) {
            $c = $Part.GetPixel($xx, $yy)
            if ($c.A -gt 0) { $Dst.SetPixel($X + $xx, $Y + $yy, $c) }
        }
    }
}

function Resize-Nearest {
    param([System.Drawing.Bitmap]$B, [int]$Dw, [int]$Dh)
    $dst = [System.Drawing.Bitmap]::new($Dw, $Dh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    for ($y = 0; $y -lt $Dh; $y++) {
        $sy = [int][math]::Floor($y * $B.Height / $Dh)
        for ($x = 0; $x -lt $Dw; $x++) {
            $sx = [int][math]::Floor($x * $B.Width / $Dw)
            $dst.SetPixel($x, $y, $B.GetPixel($sx, $sy))
        }
    }
    return $dst
}

function Add-Groove {
    param([System.Drawing.Bitmap]$B)
    $dst = [System.Drawing.Bitmap]::new($B)
    $y = [int]($dst.Height / 2) - 1
    for ($yy = $y; $yy -le $y + 1; $yy++) {
        for ($x = 1; $x -lt $dst.Width - 1; $x++) {
            $dst.SetPixel($x, $yy, [System.Drawing.Color]::FromArgb(255, $BG0[0], $BG0[1], $BG0[2]))
        }
    }
    return $dst
}

function Get-Sha { param([string]$Path) return (Get-FileHash -Algorithm SHA256 $Path).Hash.ToLower() }

# ---------- tiny authored pieces (palette-consistent) ----------
function New-Transparent {
    param([int]$W, [int]$H)
    return [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
}
function Put-Px {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int[]]$Rgb, [int]$A = 255)
    if ($X -ge 0 -and $X -lt $Bmp.Width -and $Y -ge 0 -and $Y -lt $Bmp.Height) {
        $Bmp.SetPixel($X, $Y, [System.Drawing.Color]::FromArgb($A, $Rgb[0], $Rgb[1], $Rgb[2]))
    }
}
function Fill-Rect {
    param([System.Drawing.Bitmap]$Bmp, [int]$X, [int]$Y, [int]$W, [int]$H, [int[]]$Rgb, [int]$A = 255)
    for ($yy = $Y; $yy -lt $Y + $H; $yy++) {
        for ($xx = $X; $xx -lt $X + $W; $xx++) { Put-Px $Bmp $xx $yy $Rgb $A }
    }
}
function Add-Handle {
    $d = New-Transparent 14 72
    # flat left edge + full body
    Fill-Rect $d 0 0 14 72 $OUT
    Fill-Rect $d 2 2 10 68 $WOOD0
    Fill-Rect $d 12 2 2 68 $GOLD1
    # top cap (0..11), 8x8 icon area at (3,2)
    Fill-Rect $d 1 0 12 12 $OUT
    Fill-Rect $d 2 1 10 10 $GOLD1
    Fill-Rect $d 3 2 8 8 $BG0
    # bottom cap (60..71), pull slot at (4,62) 6x8
    Fill-Rect $d 1 60 12 12 $OUT
    Fill-Rect $d 2 61 10 10 $GOLD1
    Fill-Rect $d 4 62 6 8 $BG0
    # sparse grain only in the stretchable middle
    foreach ($yy in @(20, 28, 36, 44, 52)) { Put-Px $d 6 $yy $WOOD1; Put-Px $d 8 ($yy + 2) $WOOD1 }
    return $d
}
function Add-Close {
    $d = New-Transparent 14 14
    # fully opaque plate, no corner holes
    Fill-Rect $d 0 0 14 14 $OUT
    Fill-Rect $d 2 2 10 10 $GOLD1
    Fill-Rect $d 3 3 8 8 $WOOD0
    Fill-Rect $d 4 4 6 6 $WOOD1
    # 2px bone X, 5x5 footprint
    for ($i = 0; $i -lt 5; $i++) {
        Put-Px $d (4 + $i) (4 + $i) $BONE; Put-Px $d (5 + $i) (4 + $i) $BONE
        Put-Px $d (9 - $i) (4 + $i) $BONE; Put-Px $d (8 - $i) (4 + $i) $BONE
    }
    return $d
}
function Add-Tab {
    param([bool]$Active)
    $d = New-Transparent 32 18
    Fill-Rect $d 0 0 32 18 $OUT
    if ($Active) {
        Fill-Rect $d 2 2 28 14 $GOLD1
        Fill-Rect $d 4 4 24 10 $WOOD1
        Fill-Rect $d 6 6 20 1 $GOLD2
        Fill-Rect $d 6 11 20 1 $WOOD0
    } else {
        Fill-Rect $d 2 2 28 14 $WOOD0
        Fill-Rect $d 4 4 24 10 $BG1
        Fill-Rect $d 6 6 20 1 $OUT
        Fill-Rect $d 6 11 20 1 $BG3
    }
    # corner studs only (outside 6..25 / 6..11 stretch zone)
    $stud = if ($Active) { $GOLD2 } else { $GOLD0 }
    foreach ($p in @(@(3,3),@(27,3),@(3,13),@(27,13))) { Fill-Rect $d $p[0] $p[1] 2 2 $stud }
    return $d
}
function Add-Inset {
    $d = New-Transparent 24 24
    Fill-Rect $d 0 0 24 24 $OUT
    Fill-Rect $d 2 2 20 20 $BG1
    Fill-Rect $d 2 2 20 2 $BG0
    Fill-Rect $d 2 2 2 20 $BG0
    Fill-Rect $d 2 20 20 2 $BG3
    Fill-Rect $d 20 2 2 20 $BG3
    return $d
}
function Add-Slot {
    $d = New-Transparent 36 36
    Fill-Rect $d 0 0 36 36 $OUT
    Fill-Rect $d 2 2 32 32 $GOLD0
    Fill-Rect $d 4 4 28 28 $BG0
    Fill-Rect $d 4 30 28 2 $BG3
    Fill-Rect $d 30 4 2 28 $BG3
    Fill-Rect $d 6 6 24 24 $BG2
    return $d
}
function Add-Divider {
    $d = New-Transparent 24 4
    Fill-Rect $d 0 0 24 2 $BG0
    Fill-Rect $d 0 2 24 2 $GOLD0
    Fill-Rect $d 0 0 2 2 $OUT
    Fill-Rect $d 22 0 2 2 $OUT
    Fill-Rect $d 0 2 2 2 $GOLD1
    Fill-Rect $d 22 2 2 2 $GOLD1
    return $d
}
function Add-TitlePlate {
    $d = New-Transparent 48 18
    Fill-Rect $d 0 0 48 18 $OUT
    Fill-Rect $d 2 2 44 14 $GOLD1
    Fill-Rect $d 4 4 40 10 $WOOD0
    Fill-Rect $d 4 4 40 1 $WOOD1
    foreach ($x in @(6, 40)) { Fill-Rect $d $x 4 2 2 $GOLD2; Fill-Rect $d $x 12 2 2 $GOLD2 }
    return $d
}
function Add-Track {
    $d = New-Transparent 16 16
    Fill-Rect $d 0 0 16 16 $OUT
    Fill-Rect $d 2 2 12 12 $BG0
    Fill-Rect $d 4 4 8 8 $BG1
    Fill-Rect $d 2 7 12 2 $BG0
    return $d
}
function Add-Panel {
    $d = New-Transparent 64 64
    Fill-Rect $d 0 0 64 64 $OUT
    Fill-Rect $d 1 1 62 62 $GOLD1
    Fill-Rect $d 2 2 60 60 $GOLD0
    Fill-Rect $d 3 3 58 58 $WOOD0
    Fill-Rect $d 4 4 56 56 $BG1
    # top/left highlight, bottom/right shadow
    Fill-Rect $d 2 2 60 1 $GOLD2
    Fill-Rect $d 2 2 1 60 $GOLD2
    Fill-Rect $d 3 60 58 1 $WOOD0
    Fill-Rect $d 60 3 1 58 $WOOD0
    # gold dots
    for ($x = 8; $x -lt 56; $x += 8) { Fill-Rect $d $x 2 2 1 $GOLD2 }
    for ($y = 8; $y -lt 56; $y += 8) { Fill-Rect $d 2 $y 1 2 $GOLD2 }
    # corner rivets
    foreach ($p in @(@(5,5),@(56,5),@(5,56),@(56,56))) { Fill-Rect $d $p[0] $p[1] 2 2 $GOLD2 }
    return $d
}

# ---------- build one asset ----------
function Build-Asset {
    param([string]$Id, [string]$Purpose,
          [int]$Sx, [int]$Sy, [int]$Sw, [int]$Sh,
          [int]$L, [int]$R, [int]$T, [int]$B,
          [int]$Dw, [int]$Dh,
          [hashtable]$MapTable = $Map, [double]$Shift = 1.0,
          [bool]$TransparentCenter = $false, [int]$Cx = 0, [int]$Cy = 0, [int]$Cw = 0, [int]$Ch = 0,
          [bool]$Groove = $false)
    $base = Build-NineSlice $Src $Sx $Sy $Sw $Sh $L $R $T $B $Dw $Dh
    $col = Recolor-Bmp $base $MapTable $Shift
    $base.Dispose()
    if ($TransparentCenter) {
        for ($yy = $Cy; $yy -lt $Cy + $Ch; $yy++) {
            for ($xx = $Cx; $xx -lt $Cx + $Cw; $xx++) {
                if ($xx -ge 0 -and $xx -lt $col.Width -and $yy -ge 0 -and $yy -lt $col.Height) {
                    $col.SetPixel($xx, $yy, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
                }
            }
        }
    }
    if ($Groove) {
        $tmp = Add-Groove -B $col
        $col.Dispose(); $col = $tmp
    }
    $path1 = Join-Path $Out1x ($Id + '.png')
    $col.Save($path1, [System.Drawing.Imaging.ImageFormat]::Png)
    $w125 = [int][math]::Round($Dw * 1.25); $h125 = [int][math]::Round($Dh * 1.25)
    if ($Dw -eq 36 -and $Dh -eq 36) { $w125 = 44; $h125 = 44 }
    $up = Resize-Nearest $col $w125 $h125
    $path125 = Join-Path $Out125 ($Id + '.png')
    $up.Save($path125, [System.Drawing.Imaging.ImageFormat]::Png)
    $up.Dispose()
    Copy-Item $path1 (Join-Path $CopyDir ($Id + '.png')) -Force
    $row = [pscustomobject]@{
        id = $Id; purpose = $Purpose
        file_1x = 'assets/art/ui/' + $Id + '.png'; w_1x = $Dw; h_1x = $Dh
        file_125 = 'assets/art/ui/1_25x/' + $Id + '.png'; w_125 = $w125; h_125 = $h125
        source_pack = 'Golden UI (Buch, OpenGameArt)'; source_file = 'ui_split.png'
        source_rect = ('{0},{1},{2},{3}' -f $Sx,$Sy,$Sw,$Sh)
        slice = ('{0}/{1}/{2}/{3}' -f $L,$R,$T,$B)
        sha256_1x = Get-Sha $path1; sha256_125 = Get-Sha $path125
        license = 'CC0 1.0'; attribution = 'Not required (Buch / OpenGameArt)'
    }
    $col.Dispose()
    return $row
}

# ---------- 12 assets ----------
function Save-Authored {
    param([string]$Id, [string]$Purpose, [System.Drawing.Bitmap]$Bmp, [int]$W125, [int]$H125, [string]$Slice, [string]$SourceNote)
    $path1 = Join-Path $Out1x ($Id + '.png')
    $Bmp.Save($path1, [System.Drawing.Imaging.ImageFormat]::Png)
    $up = Resize-Nearest $Bmp $W125 $H125
    $path125 = Join-Path $Out125 ($Id + '.png')
    $up.Save($path125, [System.Drawing.Imaging.ImageFormat]::Png); $up.Dispose()
    Copy-Item $path1 (Join-Path $CopyDir ($Id + '.png')) -Force
    return [pscustomobject]@{
        id=$Id; purpose=$Purpose
        file_1x='assets/art/ui/'+$Id+'.png'; w_1x=$Bmp.Width; h_1x=$Bmp.Height
        file_125='assets/art/ui/1_25x/'+$Id+'.png'; w_125=$W125; h_125=$H125
        source_pack='Golden UI (Buch, OpenGameArt)'; source_file='ui_split.png'
        source_rect=$SourceNote; slice=$Slice
        sha256_1x=(Get-Sha $path1); sha256_125=(Get-Sha $path125)
        license='CC0 1.0'; attribution='Not required (Buch / OpenGameArt)'
    }
}

$rows = @()
# M01 panel body: authored stone/wood frame, 12/12/12/12
$tmp = Add-Panel
$rows += Save-Authored 'ui_panel_body_9p' 'drawer panel body' $tmp 80 80 '12/12/12/12' 'authored from Golden UI palette'
$tmp.Dispose()
# M02 inset: inventory slot (dark bevel)
$tmp = Add-Inset
$rows += Save-Authored 'ui_inset_9p' 'recessed info plate' $tmp 30 30 '6/6/6/6' 'authored from Golden UI palette; 2px border repair'
$tmp.Dispose()
# M03 slot: inventory slot
$tmp = Add-Slot
$rows += Save-Authored 'ui_slot_9p' 'portrait/equipment slot' $tmp 44 44 '8/8/8/8' 'authored from Golden UI palette; 2px border repair'
$tmp.Dispose()
# M04 selected: slot border, transparent center, gold map
$rows += Build-Asset 'ui_slot_selected_9p' 'selected slot overlay' 13 140 18 18 8 8 8 8 36 36 $GoldMap 1.0 $true 4 4 28 28
# M05 active tab: gold bar
$tmp = Add-Tab $true
$rows += Save-Authored 'ui_tab_active_9p' 'active tab' $tmp 40 22 '6/6/6/6' 'authored from Golden UI palette; 9-slice-safe ends'
$tmp.Dispose()
# M06 inactive tab: same bar, darker
$tmp = Add-Tab $false
$rows += Save-Authored 'ui_tab_inactive_9p' 'inactive tab' $tmp 40 22 '6/6/6/6' 'authored from Golden UI palette; 9-slice-safe ends'
$tmp.Dispose()
# M07 track: authored, 2px groove, shared H/V (rotate 90 for vertical)
$tmp = Add-Track
$rows += Save-Authored 'ui_scroll_track_9p' 'scroll track (H/V)' $tmp 20 20 '4/4/4/4' 'authored from Golden UI palette'
$tmp.Dispose()
# M08 knob: source gem knob, recolor green to gold
$KnobMap = $Map.Clone()
$KnobMap['109,170,44'] = $GOLD1
$KnobMap['52,101,36'] = $GOLD0
$KnobMap['89,125,206'] = $STEEL
$base = Crop-Bmp $Src 266 59 12 12
$tmp = Recolor-Bmp $base $KnobMap 1.0
$base.Dispose()
$rows += Save-Authored 'ui_scroll_knob_9p' 'scroll knob (H/V)' $tmp 16 16 '4/4/4/4' '266,59,12,12; green -> gold'
$tmp.Dispose()
# M09 handle: authored wood plaque + 8x8 icon recess + lower pull slot
$tmp = Add-Handle
$rows += Save-Authored 'ui_handle' 'left drawer handle' $tmp 18 90 '0/0/12/12' 'authored from Golden UI palette; cap-only decoration repair'
$tmp.Dispose()
# M10 close: authored 14x14 red plate + 2px bone X
$tmp = Add-Close
$rows += Save-Authored 'ui_btn_close' 'close button' $tmp 18 18 'none' 'authored from Golden UI palette; no transparent gap'
$tmp.Dispose()
# M11 divider: authored 2px dark + 2px light
$tmp = Add-Divider
$rows += Save-Authored 'ui_divider_h' 'horizontal divider' $tmp 30 4 '4/4/0/0' 'authored from Golden UI palette'
$tmp.Dispose()
# M12 title plate: authored wood plaque, 8/8/6/6
$tmp = Add-TitlePlate
$rows += Save-Authored 'ui_title_plate_9p' 'title plate' $tmp 60 22 '8/8/6/6' 'authored from Golden UI palette'
$tmp.Dispose()

# ---------- manifest ----------
$csv = $rows | ConvertTo-Csv -NoTypeInformation
[IO.File]::WriteAllText((Join-Path $Root 'assets_src\ui_redesign\ui_asset_manifest.csv'), ($csv -join "`r`n") + "`r`n", (New-Object Text.UTF8Encoding($true)))
[IO.File]::WriteAllText((Join-Path $Root 'assets_src\ui_redesign\ui_asset_manifest.json'), ($rows | ConvertTo-Json -Depth 4), (New-Object Text.UTF8Encoding($false)))

# ---------- previews ----------
$Font = [System.Drawing.Font]::new('Consolas', 9)
$FontSm = [System.Drawing.Font]::new('Consolas', 7)
function New-Sheet {
    param([int]$W,[int]$H,[int]$R=22,[int]$G=22,[int]$B=28)
    $bmp=[System.Drawing.Bitmap]::new($W,$H,[System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gr=[System.Drawing.Graphics]::FromImage($bmp); $gr.Clear([System.Drawing.Color]::FromArgb($R,$G,$B))
    $gr.InterpolationMode=[System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor; $gr.PixelOffsetMode=[System.Drawing.Drawing2D.PixelOffsetMode]::Half
    return @{bmp=$bmp;g=$gr}
}
$layout = @(
    @{id='ui_panel_body_9p'; c=0; r=0}, @{id='ui_inset_9p'; c=1; r=0}, @{id='ui_slot_9p'; c=2; r=0}, @{id='ui_slot_selected_9p'; c=3; r=0},
    @{id='ui_tab_active_9p'; c=0; r=1}, @{id='ui_tab_inactive_9p'; c=1; r=1}, @{id='ui_scroll_track_9p'; c=2; r=1}, @{id='ui_scroll_knob_9p'; c=3; r=1},
    @{id='ui_handle'; c=0; r=2}, @{id='ui_btn_close'; c=1; r=2}, @{id='ui_divider_h'; c=2; r=2}, @{id='ui_title_plate_9p'; c=3; r=2}
)
foreach ($set in @(@{dir=$Out1x; tag='1x'; scale=4}, @{dir=$Out125; tag='125x'; scale=3})) {
    $cellW=100*$set.scale; $cellH=100*$set.scale
    $sh=New-Sheet (($cellW+10)*4+20) (($cellH+24)*3+20)
    $g=$sh.g
    foreach($item in $layout){
        $img=[System.Drawing.Bitmap]::new((Join-Path $set.dir ($item.id+'.png')))
        $x=10+$item.c*($cellW+10); $y=10+$item.r*($cellH+24)
        $g.DrawImage($img,$x,$y,$img.Width*$set.scale,$img.Height*$set.scale)
        $g.DrawString($item.id,$FontSm,[System.Drawing.Brushes]::White,$x,$y+$cellH+2)
        $g.DrawString(('{0}x{1}' -f $img.Width,$img.Height),$FontSm,[System.Drawing.Brushes]::LightGray,$x,$y+$cellH+12)
        $img.Dispose()
    }
    $g.Dispose(); $sh.bmp.Save((Join-Path $PrevDir ('ui_assets_'+$set.tag+'.png')),[System.Drawing.Imaging.ImageFormat]::Png); $sh.bmp.Dispose()
}
# mock drawer 1x (use 9-slice builds for panel/inset so corners are not stretched)
function Draw-NineAt {
    param([System.Drawing.Graphics]$G,[System.Drawing.Bitmap]$S,[int]$Sx,[int]$Sy,[int]$Sw,[int]$Sh,[int]$L,[int]$R,[int]$T,[int]$B,[int]$X,[int]$Y,[int]$W,[int]$H,[double]$Shift)
    $tmpBmp=Build-NineSlice $S $Sx $Sy $Sw $Sh $L $R $T $B $W $H
    $tmpCol=Recolor-Bmp $tmpBmp $Map $Shift
    $tmpBmp.Dispose()
    $G.DrawImage($tmpCol,$X,$Y,$W,$H)
    $tmpCol.Dispose()
}
$mock=New-Sheet 640 360 20 24 32
$g=$mock.g
$g.Clear([System.Drawing.Color]::FromArgb(24,28,36))
# panel: authored 64x64 tile stretched with 12px 9-slice
$pnl=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_panel_body_9p.png'))
$pnl2=Build-NineSlice $pnl 0 0 64 64 12 12 12 12 244 218
$g.DrawImage($pnl2,10,60,244,218)
$pnl2.Dispose(); $pnl.Dispose()
# title
$tb=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_title_plate_9p.png')); $g.DrawImage($tb,22,70,100,18); $tb.Dispose()
# inset description
Draw-NineAt $g $Src 13 140 18 18 6 6 6 6 22 100 120 60 0.70
# tabs
$ta=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_tab_active_9p.png')); $g.DrawImage($ta,200,76,32,18); $ta.Dispose()
$ti=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_tab_inactive_9p.png')); $g.DrawImage($ti,200,96,32,18); $ti.Dispose()
# slots row
$sb=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_slot_9p.png'))
for($i=0;$i -lt 6;$i++){ $g.DrawImage($sb,22+$i*38,200,36,36) }
$sb.Dispose()
# selected
$sel=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_slot_selected_9p.png')); $g.DrawImage($sel,22+2*38,200,36,36); $sel.Dispose()
# divider
$dv=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_divider_h.png')); $g.DrawImage($dv,22,190,220,4); $dv.Dispose()
# close
$cl=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_btn_close.png')); $g.DrawImage($cl,226,66,14,14); $cl.Dispose()
# handle
$hd=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_handle.png')); $g.DrawImage($hd,-4,110,14,72); $hd.Dispose()
# scroll track knob
$tr=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_scroll_track_9p.png')); $g.DrawImage($tr,20,270,220,16); $tr.Dispose()
$kn=[System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_scroll_knob_9p.png')); $g.DrawImage($kn,120,272,12,12); $kn.Dispose()
$g.Dispose()
$mock.bmp.Save((Join-Path $PrevDir 'mock_drawer_1x.png'),[System.Drawing.Imaging.ImageFormat]::Png)
$up=Resize-Nearest $mock.bmp 1280 720; $up.Save((Join-Path $PrevDir 'mock_drawer_1x_2x.png'),[System.Drawing.Imaging.ImageFormat]::Png); $up.Dispose(); $mock.bmp.Dispose()

Write-Output ("OK assets={0} out1x={1} out125={2}" -f $rows.Count,$Out1x,$Out125)
