# Third-party notices

SurvEye includes the following third-party material in the Java engine or generated HTML.

## Chart.js 4.4.9

Project: <https://www.chartjs.org/>  
Copyright © 2014–2024 Chart.js Contributors  
License: MIT

```text
The MIT License (MIT)

Copyright (c) 2014-2024 Chart.js Contributors

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
```

The minified distribution retained in `src/resources/chart.umd.js` carries its upstream version and license banner. It is embedded into generated dashboards, so chart rendering does not depend on a CDN.

## Leaflet 1.9.4

Project: <https://leafletjs.com/>  
Copyright © 2010–2023 Volodymyr Agafonkin  
Copyright © 2010–2011 CloudMade  
License: BSD 2-Clause

```text
BSD 2-Clause License

Copyright (c) 2010-2023, Volodymyr Agafonkin
Copyright (c) 2010-2011, CloudMade
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice, this
   list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
   this list of conditions and the following disclaimer in the documentation
   and/or other materials provided with the distribution.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
```

The upstream JavaScript and CSS distributions are retained in
`src/resources/leaflet.js` and `src/resources/leaflet.css`, embedded in the Java
engine, and then embedded in map-enabled dashboards.

## Noto Sans Arabic 5.2.10

Project: <https://fonts.google.com/noto/specimen/Noto+Sans+Arabic>  
Distribution: `@fontsource/noto-sans-arabic` 5.2.10  
Copyright © The Noto Project Authors  
License: SIL Open Font License 1.1

The Arabic-script regular and bold WOFF2 subsets are bundled in
`src/resources/` and embedded as data URLs only when a generated dashboard
needs Arabic or Urdu glyphs. This keeps RTL output self-contained and avoids
depending on operating-system fonts. The complete license text is retained in
`src/resources/NOTO-SANS-ARABIC-LICENSE.txt` and inside the Java JAR.

## Online base-map tiles

Map-enabled dashboards can request Google Hybrid, Google Satellite, Google
Roads, or OpenStreetMap tiles through Leaflet. The tile imagery is fetched by
the user's browser when the map opens; it is not copied into or redistributed
with this package. The generated layer control displays the provider
attribution. Users are responsible for complying with the selected provider's
terms, attribution requirements, access policies, and any organizational
restrictions on external map services.

The Google choices intentionally mirror the keyless compatibility endpoints
used by `esqc_gps`. They are not an authenticated Google Maps Platform
integration and are not represented as an official or guaranteed no-key
service. Their availability and permitted use may change. Confirm provider
authorization before distributing a dashboard; select an approved alternative
such as `basemap(osm)` when appropriate. See the current
[Google Map Tiles API documentation](https://developers.google.com/maps/documentation/tile)
and [OpenStreetMap tile-use policy](https://operations.osmfoundation.org/policies/tiles/).

Google and OpenStreetMap are not affiliated with this package. Google is a
trademark of Google LLC. OpenStreetMap is open data licensed under the Open Data
Commons Open Database License by the OpenStreetMap Foundation and contributors;
see <https://www.openstreetmap.org/copyright>.

## Natural Earth 1:50m country boundaries

Project: <https://www.naturalearthdata.com/>  
Material: generalized 1:50m country-outline geometry bundled as `world50.tsv`  
Terms: Natural Earth states that its data are in the public domain; see <https://www.naturalearthdata.com/about/terms-of-use/>.

The geometry is used only when a user does not supply a more detailed boundary file. Country and territory names or boundaries do not imply any position by the author or the World Bank concerning legal status, sovereignty, or boundary delimitation.

## User-supplied boundaries and logos

Files passed through `boundaries()` or `logo()` are not distributed with this software. Users are responsible for permission, attribution, boundary policy, and disclosure review for those materials and for any generated dashboard that embeds them.

## Java and Stata

The package calls the Java runtime bundled/configured by Stata through Stata's public `javacall` interface. It does not redistribute Stata or a Java runtime. Stata is a registered trademark of StataCorp LLC. Survey Solutions is a World Bank data-collection platform.
