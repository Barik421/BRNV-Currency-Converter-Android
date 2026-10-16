# Reuses the app's existing arrow mark; no external fonts or stock assets.
Add-Type -AssemblyName System.Drawing
$outDir = Join-Path $PSScriptRoot 'assets'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null
function Draw-Mark($g, [single]$x, [single]$y, [single]$size) {
    $state = $g.Save()
    $g.TranslateTransform($x, $y)
    $g.ScaleTransform(($size / 108), ($size / 108))
    $red = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#CE233A'))
    $g.FillRectangle($red, 0, 0, 108, 108)
    $pen = [Drawing.Pen]::new([Drawing.Color]::White, 5)
    $pen.StartCap = $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
    $pen.LineJoin = [Drawing.Drawing2D.LineJoin]::Round
    $g.DrawLine($pen, 35, 43, 73, 43)
    $g.DrawLines($pen, [Drawing.PointF[]]@([Drawing.PointF]::new(63,33),[Drawing.PointF]::new(73,43),[Drawing.PointF]::new(63,53)))
    $g.DrawLine($pen, 73, 65, 35, 65)
    $g.DrawLines($pen, [Drawing.PointF[]]@([Drawing.PointF]::new(45,55),[Drawing.PointF]::new(35,65),[Drawing.PointF]::new(45,75)))
    $pen.Dispose(); $red.Dispose(); $g.Restore($state)
}
$icon = [Drawing.Bitmap]::new(512,512)
$g = [Drawing.Graphics]::FromImage($icon)
$g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
Draw-Mark $g 0 0 512
$icon.Save((Join-Path $outDir 'icon-512.png'), [Drawing.Imaging.ImageFormat]::Png)
$g.Dispose(); $icon.Dispose()
foreach ($locale in @('uk','en')) {
    $bitmap = [Drawing.Bitmap]::new(1024,500,[Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [Drawing.Graphics]::FromImage($bitmap)
    $g.Clear([Drawing.ColorTranslator]::FromHtml('#141416'))
    $g.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    Draw-Mark $g 64 64 84
    $white = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#F7F7F8'))
    $muted = [Drawing.SolidBrush]::new([Drawing.ColorTranslator]::FromHtml('#B8BAC1'))
    $title = [Drawing.Font]::new('Segoe UI',42,[Drawing.FontStyle]::Bold,[Drawing.GraphicsUnit]::Pixel)
    $sub = [Drawing.Font]::new('Segoe UI',30,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
    $small = [Drawing.Font]::new('Segoe UI',22,[Drawing.FontStyle]::Regular,[Drawing.GraphicsUnit]::Pixel)
    $g.DrawString('BRNV',$title,$white,168,75)
    $g.DrawString('Currency Converter',$title,$white,60,188)
    $tagline = if ($locale -eq 'uk') { 'Конвертація без зайвого.' } else { 'Conversion. Simply.' }
    $detail = if ($locale -eq 'uk') { 'Валюти · Криптовалюти · Віджети' } else { 'Currencies · Crypto · Widgets' }
    $g.DrawString($tagline,$sub,$white,62,267)
    $g.DrawString($detail,$small,$muted,64,383)
    $bitmap.Save((Join-Path $outDir "feature-$locale.png"),[Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bitmap.Dispose(); $white.Dispose(); $muted.Dispose(); $title.Dispose(); $sub.Dispose(); $small.Dispose()
}
