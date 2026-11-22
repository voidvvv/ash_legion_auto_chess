# UI asset previews + 9-slice stretch test (explicit layout; no shared loop vars)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$Out1x = Join-Path $Root 'assets\art\ui'
$Out125 = Join-Path $Root 'assets\art\ui\1_25x'
$Prev = Join-Path $Root 'assets_src\ui_redesign\previews'
New-Item -ItemType Directory -Force $Prev | Out-Null

function New-Canvas([int]$W, [int]$H) {
    $b = [System.Drawing.Bitmap]::new($W, $H, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $g = [System.Drawing.Graphics]::FromImage($b)
    $g.Clear([System.Drawing.Color]::FromArgb(22, 26, 34))
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor
    $g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::Half
    return @{b=$b;g=$g}
}
function Put-Image($Canvas, [string]$Path, [int]$X, [int]$Y, [int]$Scale) {
    $img = [System.Drawing.Bitmap]::new($Path)
    $Canvas.g.DrawImage($img, $X, $Y, $img.Width * $Scale, $img.Height * $Scale)
    $Canvas.g.DrawString([IO.Path]::GetFileNameWithoutExtension($Path), [System.Drawing.Font]::new('Consolas', 8), [System.Drawing.Brushes]::White, $X, $Y + $img.Height * $Scale + 2)
    $Canvas.g.DrawString(('{0}x{1}' -f $img.Width, $img.Height), [System.Drawing.Font]::new('Consolas', 7), [System.Drawing.Brushes]::LightGray, $X, $Y + $img.Height * $Scale + 12)
    $img.Dispose()
}
function New-Sheet([string]$Tag, [string]$Dir, [int]$Scale) {
    $c = New-Canvas 760 1260
    Put-Image $c (Join-Path $Dir 'ui_panel_body_9p.png') 20 20 $Scale
    Put-Image $c (Join-Path $Dir 'ui_inset_9p.png') 280 20 $Scale
    Put-Image $c (Join-Path $Dir 'ui_slot_9p.png') 540 20 $Scale
    Put-Image $c (Join-Path $Dir 'ui_slot_selected_9p.png') 20 300 $Scale
    Put-Image $c (Join-Path $Dir 'ui_tab_active_9p.png') 280 300 $Scale
    Put-Image $c (Join-Path $Dir 'ui_tab_inactive_9p.png') 540 300 $Scale
    Put-Image $c (Join-Path $Dir 'ui_scroll_track_9p.png') 20 560 $Scale
    Put-Image $c (Join-Path $Dir 'ui_scroll_knob_9p.png') 280 560 $Scale
    Put-Image $c (Join-Path $Dir 'ui_handle.png') 540 560 $Scale
    Put-Image $c (Join-Path $Dir 'ui_btn_close.png') 20 870 $Scale
    Put-Image $c (Join-Path $Dir 'ui_divider_h.png') 280 870 $Scale
    Put-Image $c (Join-Path $Dir 'ui_title_plate_9p.png') 540 870 $Scale
    $c.g.Dispose()
    $c.b.Save((Join-Path $Prev ('ui_assets_' + $Tag + '.png')), [System.Drawing.Imaging.ImageFormat]::Png)
    $c.b.Dispose()
}
New-Sheet '1x' $Out1x 4
New-Sheet '125x' $Out125 3

# ---------- mock drawer 1x ----------
function Draw-NineTile($G, [string]$Path, [int]$Dx, [int]$Dy, [int]$Dw, [int]$Dh, [int]$L, [int]$R, [int]$T, [int]$B) {
    $img = [System.Drawing.Bitmap]::new($Path)
    $tmp = [System.Drawing.Bitmap]::new($Dw, $Dh, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $sw = $img.Width; $sh = $img.Height
    $cw = [math]::Max(1, $sw - $L - $R); $ch = [math]::Max(1, $sh - $T - $B)
    for ($y = 0; $y -lt $Dh; $y++) {
        if ($y -lt $T) { $sy = $y } elseif ($y -ge $Dh - $B) { $sy = $sh - ($Dh - $y) } else { $sy = $T + (($y - $T) % $ch) }
        for ($x = 0; $x -lt $Dw; $x++) {
            if ($x -lt $L) { $sx = $x } elseif ($x -ge $Dw - $R) { $sx = $sw - ($Dw - $x) } else { $sx = $L + (($x - $L) % $cw) }
            $c = $img.GetPixel($sx, $sy); if ($c.A -gt 0) { $tmp.SetPixel($x, $y, $c) }
        }
    }
    $G.DrawImage($tmp, $Dx, $Dy, $Dw, $Dh)
    $tmp.Dispose(); $img.Dispose()
}
$m = New-Canvas 640 360
$g = $m.g
$g.Clear([System.Drawing.Color]::FromArgb(18, 22, 30))
Draw-NineTile $g (Join-Path $Out1x 'ui_panel_body_9p.png') 10 60 244 218 12 12 12 12
# title (native 48x18) and inset (9-slice)
$title = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_title_plate_9p.png')); $g.DrawImage($title, 22, 70, 48, 18); $title.Dispose()
Draw-NineTile $g (Join-Path $Out1x 'ui_inset_9p.png') 22 100 120 60 6 6 6 6
# tabs
$ta = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_tab_active_9p.png')); $g.DrawImage($ta, 200, 76, 32, 18); $ta.Dispose()
$ti = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_tab_inactive_9p.png')); $g.DrawImage($ti, 200, 96, 32, 18); $ti.Dispose()
# divider
$dv = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_divider_h.png')); $g.DrawImage($dv, 22, 186, 220, 4); $dv.Dispose()
# slots + selected
$sl = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_slot_9p.png'))
for ($i = 0; $i -lt 6; $i++) { $g.DrawImage($sl, 22 + $i * 38, 200, 36, 36) }
$sl.Dispose()
$se = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_slot_selected_9p.png')); $g.DrawImage($se, 22 + 2 * 38, 200, 36, 36); $se.Dispose()
# scrollbar track + knob
Draw-NineTile $g (Join-Path $Out1x 'ui_scroll_track_9p.png') 22 268 220 16 4 4 4 4
$kn = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_scroll_knob_9p.png')); $g.DrawImage($kn, 120, 270, 12, 12); $kn.Dispose()
# close + handle
$cl = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_btn_close.png')); $g.DrawImage($cl, 226, 66, 14, 14); $cl.Dispose()
$hd = [System.Drawing.Bitmap]::new((Join-Path $Out1x 'ui_handle.png')); $g.DrawImage($hd, -4, 110, 14, 72); $hd.Dispose()
$g.Dispose()
$m.b.Save((Join-Path $Prev 'mock_drawer_1x.png'), [System.Drawing.Imaging.ImageFormat]::Png)
$up = [System.Drawing.Bitmap]::new(1280, 720); $gu = [System.Drawing.Graphics]::FromImage($up); $gu.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::NearestNeighbor; $gu.DrawImage($m.b, 0, 0, 1280, 720); $gu.Dispose()
$up.Save((Join-Path $Prev 'mock_drawer_1x_2x.png'), [System.Drawing.Imaging.ImageFormat]::Png); $up.Dispose(); $m.b.Dispose()

# ---------- 9-slice stretch proof ----------
$p = New-Canvas 640 300
Draw-NineTile $p.g (Join-Path $Out1x 'ui_panel_body_9p.png') 20 20 244 218 12 12 12 12
Draw-NineTile $p.g (Join-Path $Out1x 'ui_inset_9p.png') 290 20 120 60 6 6 6 6
Draw-NineTile $p.g (Join-Path $Out1x 'ui_slot_9p.png') 290 100 36 36 8 8 8 8
Draw-NineTile $p.g (Join-Path $Out1x 'ui_slot_9p.png') 340 100 72 36 8 8 8 8
Draw-NineTile $p.g (Join-Path $Out1x 'ui_slot_9p.png') 290 150 36 72 8 8 8 8
Draw-NineTile $p.g (Join-Path $Out1x 'ui_scroll_track_9p.png') 290 240 220 16 4 4 4 4
$p.g.Dispose(); $p.b.Save((Join-Path $Prev 'panel_9slice_stretch_test.png'), [System.Drawing.Imaging.ImageFormat]::Png); $p.b.Dispose()

Write-Output 'previews OK'
