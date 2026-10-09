$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$projectRoot = Split-Path $PSScriptRoot -Parent
$bitmap = [System.Drawing.Bitmap]::new(320, 180)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
$fonts = [System.Drawing.Text.PrivateFontCollection]::new()
$fonts.AddFontFile((Join-Path $projectRoot 'app/src/main/res/font/unbounded.ttf'))
$fonts.AddFontFile((Join-Path $projectRoot 'app/src/main/res/font/manrope.ttf'))
$pink = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#FF2DAB'))
$white = [System.Drawing.Pen]::new([System.Drawing.Color]::White, 3)
$violet = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml('#BE8AFF'), 3)
$muted = [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml('#BE8AFF'))
$titleFont = [System.Drawing.Font]::new(($fonts.Families | Where-Object Name -eq 'Unbounded' | Select-Object -First 1), 29, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$bodyFont = [System.Drawing.Font]::new(($fonts.Families | Where-Object Name -eq 'Manrope' | Select-Object -First 1), 12, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
try {
    $graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#180C2B'))
    $white.StartCap = $white.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $violet.StartCap = $violet.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $graphics.TranslateTransform(128, 18)
    $graphics.FillEllipse($pink, 23, 12, 18, 18)
    $graphics.FillRectangle($pink, 23, 21, 18, 11)
    $graphics.FillEllipse($pink, 23, 24, 18, 18)
    $graphics.DrawArc($white, 18, 17, 28, 29, 0, 180)
    $graphics.DrawLine($white, 18, 29, 18, 31)
    $graphics.DrawLine($white, 46, 29, 46, 31)
    $graphics.DrawLine($white, 32, 46, 32, 53)
    $graphics.DrawLine($white, 25, 53, 39, 53)
    $graphics.DrawLine($white, 29, 22, 35, 22)
    $graphics.DrawLine($white, 29, 28, 35, 28)
    $graphics.DrawLine($violet, 11, 23, 11, 34)
    $graphics.DrawLine($violet, 53, 23, 53, 34)
    $graphics.DrawLine($violet, 6, 27, 6, 30)
    $graphics.DrawLine($violet, 58, 27, 58, 30)
    $graphics.ResetTransform()
    $format = [System.Drawing.StringFormat]::new()
    $format.Alignment = [System.Drawing.StringAlignment]::Center
    $graphics.DrawString('SoltaVoz', $titleFont, $pink, [System.Drawing.RectangleF]::new(0, 90, 320, 45), $format)
    $graphics.DrawString('SEU PALCO. SUA VOZ.', $bodyFont, $muted, [System.Drawing.RectangleF]::new(0, 138, 320, 25), $format)
    $bitmap.Save((Join-Path $projectRoot 'app/src/main/res/drawable/tv_banner.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $format.Dispose()
} finally {
    $titleFont.Dispose(); $bodyFont.Dispose(); $fonts.Dispose()
    $pink.Dispose(); $white.Dispose(); $violet.Dispose(); $muted.Dispose()
    $graphics.Dispose(); $bitmap.Dispose()
}
