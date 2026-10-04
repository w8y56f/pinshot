---
name: pinshot-release
description: Release PinShot by checking committed code, obtaining confirmation before committing pending changes, building a versioned APK, pushing the matching Git tag, and uploading the APK to GitHub Releases. Use when asked to release, package, or tag PinShot.
---

# PinShot release

Use this skill only in the `pinshot` project. The project version is defined by `versionName` and `versionCode` in `app/build.gradle.kts`.

## Release procedure

1. Read `versionName` and `versionCode` from `app/build.gradle.kts`. Keep the tag and APK filename versioned from this source; do not hard-code a version in commands. Do not silently change the version. If changing the version is requested, update Gradle and relevant README version references, increasing `versionCode` for an installable update.
2. Before packaging, inspect `git status --short --branch`, the current branch, and local and remote tags and GitHub Releases. Check untracked files as well as tracked changes using `git status --porcelain`. If there are uncommitted changes, finish preparing the release changes, summarize the exact files and proposed commit, and ask the user to confirm committing them. Do not commit or package until confirmation is received; existing explicit approval for these same changes satisfies this requirement. Stage only the confirmed files, commit them, and verify the working tree is clean. If the exact version tag or GitHub Release already exists, stop and report it instead of replacing it.
3. Capture the full release commit and its first seven characters. Confirm the intended branch and GitHub repository from `origin`. A request to publish this release authorizes pushing its confirmed commit and uploading the APK; a package-only or tag-only request does not authorize publishing a GitHub Release. For a requested release, check `gh auth status`, push the current branch to `origin` before building the APK, and verify the remote branch points to the captured release commit. Do not force-push. A package-only request ends after APK verification; a tag-only request performs only authorized tag actions.
4. Build the APK from the clean release commit:

   ```sh
   ./gradlew :app:assembleDebug --console=plain
   ```

   This project currently has no release signing configuration. The debug APK is signed with Android's debug key; do not describe it as a production-signed APK. If release signing is configured later, use `:app:assembleRelease` and verify that signature instead. Never upload an unsigned APK. If a production-signed release is requested, explain that a private release keystore and credentials must first be configured securely, and never create or commit a keystore or credentials.

5. Copy the built APK into the same output directory using the version name:

   ```text
   app/build/outputs/apk/debug/pinshot-<versionName>.apk
   ```

   Keep the original `app-debug.apk`. The output directory is ignored by Git. The clean debug-signed package has no `-dirty` suffix; its embedded commit ID must match the release commit.
6. Check the APK is non-empty, verify its package version and signature, and confirm generated `BuildConfig.GIT_COMMIT` matches the release commit and `GIT_DIRTY` is false. Record the APK size and SHA-256 digest. Confirm `HEAD` and the clean working tree have not changed since the build; otherwise stop and rebuild from a confirmed clean commit. Do not commit the APK.
7. Create an annotated tag named exactly `<versionName>` on the captured release commit, then push that tag:

   ```sh
   git tag -a <versionName> <releaseCommit> -m "PinShot <versionName>"
   git push origin refs/tags/<versionName>
   ```

   Never add a `v` prefix or force-push/move existing tags. If a push fails, report the actual repository state.
8. For a requested release, publish a GitHub Release using the confirmed repository and existing tag, and upload the versioned APK. Use `gh release create <versionName> <apkPath> --repo <owner/repo> --verify-tag --title "PinShot <versionName>" --notes-file <notesPath>`. Keep temporary notes in the ignored build directory. Notes should describe the changes, versionCode, release commit, SHA-256, and debug signing. Do not call a debug-signed APK production-signed.
9. Verify the remote tag targets the captured commit, the release is published, and its APK asset is present. Report the release URL, commit, APK path, signature type, and checksum. Change repository visibility only when the user explicitly requests it; perform that change after successful publication and verify the resulting visibility.

## Version format

Use the Gradle `versionName` exactly, such as `0.9.0`, for both the APK suffix and Git tag. Keep `versionCode` as the monotonically increasing Android build number; do not derive it from the tag.

## Daily Android Studio Debug

Creating this skill does not execute a release. Daily Debug builds need no commit: the UI displays `<versionName> (<seven-character-commit>)`, adding `-dirty` inside the parentheses when `git status --porcelain` reports tracked or untracked changes. For example, `0.9.0 (abcdef1-dirty)` uses the actual HEAD prefix, not a fixed placeholder. Gradle generates `BuildConfig.GIT_COMMIT` and debug `GIT_DIRTY` at build time. A clean debug-signed release has no dirty suffix. The release variant hides dirty; this does not permit publishing an uncommitted working tree.

If uncommitted changes do not belong to the release, do not silently commit, discard, or stash them. Resolve the scope with the user before packaging. Ask for commit approval only after the intended files and proposed commit are concrete and reviewable. Do not publish while that approval is pending. The repository is currently `w8y56f/pinshot`; confirm it from the actual origin. On a failure, report which steps succeeded and the exact blocker; do not automatically delete or replace tags or releases.
