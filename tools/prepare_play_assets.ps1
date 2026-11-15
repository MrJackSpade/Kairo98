$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$output = Join-Path $root 'docs\play-assets'
New-Item -ItemType Directory -Path $output -Force | Out-Null

function Save-CroppedPng {
    param(
        [string] $Source,
        [string] $Destination,
        [int] $X,
        [int] $Y,
        [int] $CropWidth,
        [int] $CropHeight,
        [int] $Width,
        [int] $Height
    )
    $sourceImage = [System.Drawing.Image]::FromFile((Join-Path $root $Source))
    try {
        if ($X -lt 0 -or $Y -lt 0 -or $X + $CropWidth -gt $sourceImage.Width -or
            $Y + $CropHeight -gt $sourceImage.Height) {
            throw "Crop is outside $Source"
        }
        $result = [System.Drawing.Bitmap]::new($Width, $Height)
        try {
            $drawing = [System.Drawing.Graphics]::FromImage($result)
            try {
                $drawing.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $drawing.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $drawing.DrawImage($sourceImage,
                    [System.Drawing.Rectangle]::new(0, 0, $Width, $Height),
                    [System.Drawing.Rectangle]::new($X, $Y, $CropWidth, $CropHeight),
                    [System.Drawing.GraphicsUnit]::Pixel)
            } finally {
                $drawing.Dispose()
            }
            $result.Save((Join-Path $output $Destination), [System.Drawing.Imaging.ImageFormat]::Png)
        } finally {
            $result.Dispose()
        }
    } finally {
        $sourceImage.Dispose()
    }
}

Save-CroppedPng 'docs\evidence\library-retroid-clean-header.png' 'feature-graphic.png' 4 0 1232 602 1024 500

# The app icon is resized from the icon already shipped in Kairo98, with no new artwork.
Save-CroppedPng 'app\src\main\res\drawable-nodpi\kairo98_icon_art.png' 'app-icon.png' 6 0 1118 1118 512 512
