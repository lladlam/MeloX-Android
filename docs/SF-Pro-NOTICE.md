# SF Pro font notice

`android/app/src/main/res/font/sf_pro_vf.ttf` is a copy of the SF Pro variable
font used **only** as the icon font (the SF Symbols glyphs). Normal UI text
uses the Android system font everywhere by default; the optional MiSans
family is downloaded on demand from its GitHub release and is not bundled in
the APK.

- Copyright: Apple Inc.
- Source used for this copy:
  `https://github.com/LabVerse/LabVerse/blob/2ab7e49899c4bb06a2233a5b9d1d53bcbf19591c/Assets/TextMesh%20Pro/Fonts/SF-Pro.ttf`
- SF Pro is **not** covered by this project's GPLv3 license. It remains subject
  to Apple's font license. Distributors must confirm that they have the right to
  redistribute it; remove this file and fall back to a system font if not.

The old `sf_pro_subset.ttf` is retained only for four newer SF Symbols glyphs
that are absent from the full font.
