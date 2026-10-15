# F-Droid icon attribution

The original `fdroid-logo.svg` is the 2015 F-Droid logo from
https://gitlab.com/fdroid/artwork/-/blob/master/fdroid-logo-2015/fdroid-logo.svg
(retrieved on 2026-10-15). `UPSTREAM-REUSE.toml` records the upstream license declarations.

Copyright holders:

- 2012 William Theaker
- 2013 Robert Martinez
- 2015 Andrew Nayenko <relan@airpost.net>

The upstream image is offered under CC-BY-SA-3.0 or GPL-3.0-or-later.
This project uses the CC-BY-SA-3.0 option:
https://creativecommons.org/licenses/by-sa/3.0/

`fdroid-monochrome.svg` and `app/src/main/res/drawable/ic_fdroid.xml` are
adaptations by tecogonaz (2026), also licensed under CC-BY-SA-3.0.
The source outline is retained, shading is removed, and the eyes, ring, and
central symbol are transparent cutouts. The Vector Drawable uses the current
theme's icon tint. The icon identifies a link to this app's F-Droid listing.

Regenerate the monochrome SVG and Vector Drawable from the Android repository root:

```sh
python3 artwork/fdroid/create_monochrome.py
```

The generator is Apache-2.0; the generated artwork remains CC-BY-SA-3.0.
Other application source code remains under its stated license. This use does
not imply endorsement or affiliation with F-Droid.
