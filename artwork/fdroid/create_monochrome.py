# SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
# SPDX-License-Identifier: Apache-2.0
"""公式SVGの輪郭を使い、目・リング・中央記号を抜いた単色ベクターを生成する。"""

from pathlib import Path
import xml.etree.ElementTree as ET

directory = Path(__file__).resolve().parent
source = ET.parse(directory / "fdroid-logo.svg").getroot()
paths = {node.get("id"): node.get("d") for node in source.iter() if node.get("d")}
outline = paths["path5128"].replace("m 2.613462,1005.5987", "m 2.613462,1.2365", 1)
assert "1005.5987" not in outline


def circle(cx, cy, radius):
    return (f"M{cx-radius},{cy} a{radius},{radius} 0,1 0 {2*radius},0 "
            f"a{radius},{radius} 0,1 0 {-2*radius},0 z")


# 原図の中央記号は親groupの変換により、ローカル座標からY方向に9移動する。
symbol = paths["path4161"].replace("m 24,17.75", "M 24,26.75", 1)
symbol = symbol.replace("C 21.734004,21.415044 22.774798,20.75 24,20.75",
                        "C 21.734004,30.415044 22.774798,29.75 24,29.75")
symbol = symbol.replace("C 18.558263,28.166146 21.04791,30.25 24,30.25",
                        "C 18.558263,37.166146 21.04791,39.25 24,39.25")
compound = " ".join([outline, circle(14.375, 13.12501738, 3.375),
                     circle(33.875, 13.12501738, 3.375),
                     circle(24, 33, 10.5), circle(24, 33, 8.6), symbol])
notice = """SPDX-FileCopyrightText: 2012 William Theaker
SPDX-FileCopyrightText: 2013 Robert Martinez
SPDX-FileCopyrightText: 2015 Andrew Nayenko <relan@airpost.net>
SPDX-FileCopyrightText: 2026 tecogonaz <tecogonaz@kusugami-lab.net>
SPDX-License-Identifier: CC-BY-SA-3.0
Derived from F-Droid Artwork fdroid-logo-2015/fdroid-logo.svg.
Changes: monochrome silhouette, transparent details, removal of shading.
Source and license: artwork/fdroid/README.md"""
vector = f'''<?xml version="1.0" encoding="utf-8"?>
<!--
{notice}
-->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp"
    android:viewportWidth="48" android:viewportHeight="48">
    <path android:fillColor="#FF000000" android:fillType="evenOdd"
        android:pathData="{compound}" />
</vector>
'''
target = directory.parents[1] / "app/src/main/res/drawable/ic_fdroid.xml"
target.write_text(vector)
(directory / "fdroid-monochrome.svg").write_text(
    f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 48 48">\n'
    f'<!--\n{notice}\n-->\n'
    f'<path fill="black" fill-rule="evenodd" d="{compound}"/>\n</svg>\n'
)
print(target)
