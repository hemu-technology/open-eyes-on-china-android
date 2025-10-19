# Play Store Marketing Icon Assets

Generated from `app/src/main/res/drawable/ic_launcher_foreground.xml`.

## Files

- `playstore_icon_transparent.svg` – Source SVG at 108x108 viewport (scales to 512x512 for Play Store). Transparent background.

## Export Instructions (Android Studio / CLI)

1. Open Android Studio > New Vector Asset (optional check) or use external tool.
2. Import `playstore_icon_transparent.svg` into a graphics editor (e.g., Inkscape, Illustrator) or run CLI conversion:

```bash
# Convert to 512x512 PNG (requires ImageMagick)
convert -background none -resize 512x512 playstore_icon_transparent.svg playstore_icon_512.png
```

1. Verify no padding: PNG should have eye shape touching horizontal bounds with balanced vertical spacing.
1. Upload `playstore_icon_512.png` to Play Console as the marketing icon.

## Optional Variants

You may also want:

- Monochrome version (for adaptive icon previews) – export with only red shapes and white stars.
- Solid background version (if Play marketing icon style guide requires no transparency) – add a white rectangle behind.

To generate a solid-background PNG:

```bash
convert -size 512x512 canvas:white playstore_icon_transparent.svg -gravity center -composite playstore_icon_512_whitebg.png
```

## Troubleshooting

- Jagged edges: increase rasterization density by exporting larger (e.g., 2048px) then downscale.
- Color shift: ensure sRGB export; ImageMagick defaults are fine.
- Misalignment: confirm star group translations match latest vector positions.

## Updating

If you adjust `ic_launcher_foreground.xml`, regenerate the SVG by copying eye + star paths again.
