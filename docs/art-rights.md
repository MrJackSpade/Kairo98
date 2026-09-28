# Artwork rights

Kairo98 keeps game artwork separate from emulator code and game metadata. Source references and packaged image checksums for the optional artwork variant are recorded in `kairo98/src/withImages/assets/art/catalog-provenance-v1.json`. The standard `withoutImages` distribution omits bundled catalog images; users can download missing images for games in their own library.

A source URL, a LaunchBox database entry, or permission to download art into a personal library does not by itself authorize redistribution in a free APK or a paid Google Play build. Each bundled cover or screenshot needs a documented creator and permission or license that permits redistribution and resizing in both channels.

`tools/import_art.py` requires the source file, checksum, creator, source, license or permission basis, and affirmative rights for both channels before importing an individual asset. The original synthetic art fixtures in `docs/fixtures/art-source/` are available for importer checks and do not grant rights to any commercial game artwork.
