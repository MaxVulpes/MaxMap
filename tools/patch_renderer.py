from __future__ import annotations
from pathlib import Path
import re
import sys

path = Path("src/client/java/dev/maxvulpes/maxmap/ui/CoverageMapRenderer.java")
src = path.read_text(encoding="utf-8")

orig = src

# ---- Patch 1: safer fillImage() ----
fill_pat = re.compile(
    r"(private\s+void\s+fillImage\s*\(\s*NativeImage\s+image\s*,\s*MapCoverageManager\.CoverageGrid\s+grid\s*,\s*int\s+cellSize\s*\)\s*\{)(.*?)(\n\s*\})",
    re.S
)

m = fill_pat.search(src)
if not m:
    print("ERROR: Fant ikke fillImage(...) med forventet signatur i CoverageMapRenderer.java", file=sys.stderr)
    sys.exit(2)

replacement_body = r"""
        // Clear (transparent) - viktig for å unngå "regnbue"/støy hvis GPU-teksturen gjenbrukes
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, 0x00000000);
            }
        }

        int draw = Math.max(1, cellSize - 1); // 1px gap => lettere å skille celler

        for (int gz = 0; gz < grid.rows(); gz++) {
            for (int gx = 0; gx < grid.columns(); gx++) {
                boolean mapped = grid.isMappedAt(grid.gxMin() + gx, grid.gzMin() + gz);
                int color = mapped ? COLOR_MAPPED : COLOR_UNMAPPED;

                int x0 = gx * cellSize;
                int y0 = gz * cellSize;

                for (int yy = 0; yy < draw; yy++) {
                    int py = y0 + yy;
                    if (py < 0 || py >= height) continue;
                    for (int xx = 0; xx < draw; xx++) {
                        int px = x0 + xx;
                        if (px < 0 || px >= width) continue;
                        image.setPixelRGBA(px, py, color);
                    }
                }
            }
        }
""".rstrip("\n")

src = src[:m.start(2)] + replacement_body + src[m.end(2):]

# ---- Patch 2: recreate DynamicTexture when size changes ----
# We inject right after nextWidth/nextHeight are computed inside ensureUpToDate.
needle = re.compile(
    r"(int\s+nextWidth\s*=\s*grid\.columns\(\)\s*\*\s*cellSize\s*;\s*\n\s*int\s+nextHeight\s*=\s*grid\.rows\(\)\s*\*\s*cellSize\s*;\s*)",
    re.M
)
m2 = needle.search(src)
if not m2:
    print("ERROR: Fant ikke nextWidth/nextHeight-beregning i ensureUpToDate(...)", file=sys.stderr)
    sys.exit(3)

inject = r"""
        // Recreate GPU texture hvis størrelse endrer seg (unngår 144->192 crash)
        if (texture == null || nextWidth != width || nextHeight != height) {
            if (texture != null) {
                texture.close();
            }
            texture = new DynamicTexture(nextWidth, nextHeight, true);
            textureId = minecraft.getTextureManager().register("maxmap_grid", texture);
            width = nextWidth;
            height = nextHeight;
        }
"""
src = src[:m2.end(1)] + inject + src[m2.end(1):]

if src == orig:
    print("WARN: Ingen endringer ble gjort (uventet).", file=sys.stderr)
else:
    path.write_text(src, encoding="utf-8")
    print("OK: Patchet CoverageMapRenderer.java")
