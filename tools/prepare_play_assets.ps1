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

# The phone captures are letterboxed to Play's 9:16 and 16:9 dimensions.
# The entire original frame stays visible; nothing is cropped or stretched.
function Save-LetterboxedPhonePng {
    param([string] $Source, [string] $Destination, [int] $Width, [int] $Height)
    $sourceImage = [System.Drawing.Image]::FromFile((Join-Path $root $Source))
    try {
        $scale = [Math]::Min($Width / $sourceImage.Width, $Height / $sourceImage.Height)
        $drawWidth = [int][Math]::Round($sourceImage.Width * $scale)
        $drawHeight = [int][Math]::Round($sourceImage.Height * $scale)
        $x = [int][Math]::Floor(($Width - $drawWidth) / 2)
        $y = [int][Math]::Floor(($Height - $drawHeight) / 2)
        $result = [System.Drawing.Bitmap]::new($Width, $Height)
        try {
            $drawing = [System.Drawing.Graphics]::FromImage($result)
            try {
                $drawing.Clear([System.Drawing.Color]::Black)
                $drawing.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
                $drawing.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
                $drawing.DrawImage($sourceImage, [System.Drawing.Rectangle]::new($x, $y, $drawWidth, $drawHeight))
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

Save-LetterboxedPhonePng 'docs\evidence\phone-library.jpg' 'phone-portrait-library.png' 1080 1920
Save-LetterboxedPhonePng 'docs\evidence\phone-game-details.jpg' 'phone-portrait-game-details.png' 1080 1920
Save-LetterboxedPhonePng 'docs\evidence\phone-game-portrait.jpg' 'phone-portrait-gameplay.png' 1080 1920
Save-LetterboxedPhonePng 'docs\evidence\phone-game-landscape.jpg' 'phone-landscape-gameplay.png' 1920 1080
Save-CroppedPng 'docs\evidence\library-retroid-clean-header.png' 'feature-graphic.png' 4 0 1232 602 1024 500

# The app icon is resized from the icon already shipped in Kairo98, with no new artwork.
Save-CroppedPng 'app\src\main\res\drawable-nodpi\kairo98_icon_art.png' 'app-icon.png' 6 0 1118 1118 512 512
