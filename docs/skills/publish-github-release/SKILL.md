---
name: publish-github-release
description: Build and publish a versioned PinShot APK as a GitHub Release when the user asks to release or publish this Android project.
---

# Publish PinShot release

Use this skill for an explicitly requested PinShot release. The project version is defined in `app/build.gradle.kts`. Keep the APK filename and Git tag tied to that version: `pinshot-<versionName>.apk` and `v<versionName>` (for example, `pinshot-0.9.0.apk` and `v0.9.0`).

## Release workflow

1. Inspect `git status`, the current branch and commit, and the changes being released. Do not include unrelated or unreviewed work. Releases must point to a commit; commit intended changes when needed and authorized by the user's request. Read `versionName`, `versionCode`, and `applicationId` from `app/build.gradle.kts`; don't invent or silently bump a version. If the requested release version differs from Gradle, explain the mismatch and ask which one to use before tagging.
2. Check local and remote tags and GitHub Releases for `v<versionName>`. Never move an existing tag or replace an existing release. If it already exists, stop and report the conflict.
3. Build an installable APK. Prefer `./gradlew :app:assembleRelease` when a release signing configuration is available. Verify the APK with Android SDK `apksigner verify` before upload. This project's Gradle file currently has no release signing configuration: if the release APK is unsigned, build `./gradlew :app:assembleDebug` and verify its debug signature. The debug-signed APK is suitable for the owner's personal use; clearly identify it as debug-signed in the release summary. Never upload an unsigned APK. Do not put signing keys or passwords in source control or print secrets.
4. Copy the verified APK to a temporary or ignored output location as `pinshot-<versionName>.apk`; do not add generated APKs to normal source commits.
5. Create an annotated Git tag `v<versionName>` at the release commit, then push that commit and tag to `origin`. Confirm GitHub CLI authentication (`gh auth status`) before publishing. If GitHub access, authentication, or repository permissions prevent the push, stop and report the exact blocker; do not claim publication succeeded.
6. Create the GitHub Release and attach the APK, for example with `gh release create <tag> <apk-path> --title "PinShot <versionName>" --notes-file <notes-path>`. Keep the notes concise: version, a short summary of user-visible changes in this release, and whether the APK is release-signed or debug-signed. GitHub's source archives are generated from the tag automatically; attach the APK as the release asset.
7. Verify with `gh release view <tag>` that the release and APK asset are present. Report the version name/code, tag, released commit, signing variant, concise summary, and GitHub Release URL. Distinguish completed build/tag/push steps from any upload that failed.
