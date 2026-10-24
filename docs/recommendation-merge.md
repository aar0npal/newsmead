# Recommendation branch integration — 2026-10-08

Owner request: merge the latest `Recommendation` branch into
`measurement/scanpath-pilot` and configure the NewsData key locally for app testing.

## Integrated sources

- Existing prototype parent: `c36cee5`.
- Fetched upstream: `501e9be1f83d4d962dc4931ef9044ab1f18a3e1b`, 11 incoming commits.
- Incoming features: NewsData feed/filtering/recommendations, publisher full-text
  extraction, account fixes, Firebase configuration, product logging and explicit
  usage-log export.
- Article definition profile V2, continuation policy v3, native estimator assets,
  calibration/pilot/replay entry points and frozen protocol identities are retained.

## Local configuration

`NEWSDATA_API_KEY` is set in the ignored root `local.properties`. The value is
absent from this document and Git's tracked files. Gradle reads the property into
`BuildConfig.NEWSDATA_API_KEY`; a rebuilt APK is required after changing the key.
The compiled app uses the key to contact `https://newsdata.io/api/1/latest`.

`StudyConfig.OFFLINE_MODE=false` enables this feed. The bundled study-feed route
remains available when explicitly switched back on. `BYPASS_LOGIN=true` retains
the existing prototype launch behavior; testing account sign-in requires its
separate setting. No Firebase credentials, calibration, or camera recordings were
collected during this integration.

## Conflict resolutions and review

Ten files had conflicts. The resolution combines API configuration with existing
native-build settings, keeps research activities and the usage-share provider,
and keeps both home feed selectors and calibration/pilot/replay controls.

The article awaits publisher extraction before enabling gaze, presentation demo
or raw reading measurement; extraction failure leaves the available summary.
Work belongs to the current view lifecycle, and returned callbacks verify that
view. The extracted original is retained when translation finishes first.
Usage logging opens a new visible period after returning from the background or
browser, and one `onStop` closes it alongside existing gaze shutdown.

Gaze initialization requires a ready article, resumed screen, inactive calibration
and no demo suppression. Pending calibration restart survives backgrounding,
uses the existing 800 ms monotonic delay and checks the current view before
dispatch. Home request generation prevents an older language/region response
from replacing a newer selection. Focused source review found no remaining
actionable issue after these repairs; lifecycle/camera behavior is not device-tested.

Usage-log sharing retains its `appusage_` prefix filter and staged cache provider.
It does not bundle the private gaze/scanpath journals. No export was initiated.

## Checks and limits

- One credential-redacted NewsData probe using the app's Philippine English feed
  parameters succeeded and returned 10 articles. This was a computer-side service
  check; phone connectivity and publisher extraction still need device testing.
- Merged manifest, article/home layouts and string resources parse without
  duplicate resource names; no textual conflict markers remain.
- All 80 focused JVM checks pass (12 suites, zero failures/errors/skips), covering
  NewsData parsing/filtering, extraction and current aid/replay/consultation rules.
  This also verifies the current continuation tests after the historical
  test-only compile correction. The initial failed gate remains in its old log.
- App and Android test APKs assemble; instrumentation compiles but has not run
  on a phone. Generated `BuildConfig` matches the local property; the merged
  manifest includes Internet permission.
- Local logs, API result and APK hashes: `output/recommendation-merge-check-01/`.

No device installation, camera capture, calibration, participant measurement,
push, or human-benefit validation is part of this checkpoint.
