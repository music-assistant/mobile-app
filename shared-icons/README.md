# shared-icons

Implementation of[Music Assistant's Shared Icons spec](https://github.com/music-assistant/shared-icons).

## Adding/updating icons

When a new version of shared icons is released, new or updated icons need to be integration into
this module. The new manifest can be copied into `SharedIconTest`, and icon SVGs can be converted
to Vector Assets for use with `SharedIcons`. To do the latter (as of Android Studio 2026.1.3):

1. Right click on `androidApp`, and choose New > Vector Asset
   - Currently, Android Studio doesn't present this option for non-Android modules
2. Choose "Local file" and then select the SVG file for the new/updated icon and create the XML
3. Correct any problems with the generated XML so that the preview looks correct
   - Some Vector Assets end up referencing `currentColor` for the fill or stroke color. This should usually be changed to `#ffffffff`.
   - `DrawableColorsTest` fails the build on any color value that is not a hex literal.