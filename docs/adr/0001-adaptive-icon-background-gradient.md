# The adaptive-icon background is a gradient measured from the baked-in artwork

The launcher artwork stays baked into `ic_launcher_foreground`, and `ic_launcher_background` is a gradient measured from it rather than a flat colour (#289). A real background layer must fill 108dp, but only 71.5dp of art exists and there is no vector source, so splitting the layers means redrawing the icon. The background is invisible under static masks, so a gradient improves parallax without risking the common case.

## Consequences

`res/drawable/ic_launcher_background.xml` holds the stops `#D8F3EC` → `#A3E3D7` → `#6DD3C5`, sampled from the xxxhdpi foreground; the `@color` of the same name is gone.
