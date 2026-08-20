# Dunevault Releases

Public binary distribution channel for **Dunevault: Idle Miner Next**.

This repository intentionally contains **release metadata and downloadable release assets only**. The private source of truth remains `mrfantest2/Dunevault`.

## Canonical Android identity

- Package: `com.fantest.dunevault.next`
- Current planned release: `0.2.1` (`versionCode 201`)
- Release tag format: `v<version>`

## Update endpoint

The installed game uses:

`https://github.com/mrfantest2/Dunevault-Releases/releases/latest/download/update.json`

A certified release is expected to contain:

- `Dunevault-<version>.apk`
- `update.json`
- `certified.sha256`
- `visual-evidence.json`
- `screen.png`
- `screen.mp4` when recording succeeds

## Release policy

Assets are published only by the production workflow in `mrfantest2/Dunevault` after release-intent validation, production signer continuity verification, Android API 35 GitHub-hosted emulator install/launch, evidence generation, APK SHA-256 verification, and anonymous post-publication verification.

Do not manually replace an APK or `update.json` under an existing release tag.

No source code, signing keys, passwords, GitHub tokens, or other secrets belong in this repository.
