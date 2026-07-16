# AGENTS.md

## Static Checks

Before handing off code changes, run the relevant static checks from the repository root:

```bash
./scripts/check-static.sh --host
```

Use Android checks when touching Android, JNI, Tauri mobile generation, or Android workflows:

```bash
./scripts/check-static.sh --android
```

If only the Android generated Gradle/Kotlin/AIDL side changed, this narrower check is acceptable:

```bash
./scripts/check-static.sh --android-kotlin
```

The frontend checks expect dependencies to be installed first:

```bash
npm --prefix app ci
```
