---
inclusion: always
---

# Gradle Build Troubleshooting

## Common Build Issues and Solutions

This document covers common Gradle build issues encountered during development and their solutions.

## File Lock Issues

### Problem: "The process cannot access the file because it is being used by another process"

This is a common issue on Windows where Gradle daemon or Java processes hold locks on build artifacts (JAR files, class files, etc.).

**Symptoms:**
```
Execution failed for task ':shared:bundleLibCompileToJarDebug'.
> java.nio.file.FileSystemException: C:\Users\...\classes.jar: 
  The process cannot access the file because it is being used by another process
```

### Solution 1: Stop Gradle Daemon and Kill Java Processes

**Windows (PowerShell):**
```powershell
# Stop all Gradle daemons
.\gradlew.bat --stop

# Kill all Java processes (be careful if you have other Java apps running)
taskkill /F /IM java.exe

# Wait a moment for processes to fully terminate
Start-Sleep -Seconds 2

# Then rebuild
.\gradlew.bat androidApp:assembleDebug
```

**Combined one-liner:**
```powershell
.\gradlew.bat --stop ; taskkill /F /IM java.exe 2>$null ; Start-Sleep -Seconds 2 ; .\gradlew.bat androidApp:assembleDebug
```

**Linux/Mac:**
```bash
# Stop Gradle daemon
./gradlew --stop

# Kill Java processes (if needed)
pkill -9 java

# Wait and rebuild
sleep 2
./gradlew androidApp:assembleDebug
```

### Solution 2: Delete Build Directories

If stopping daemons doesn't work, delete the build directories:

**Windows (PowerShell):**
```powershell
# Stop everything first
.\gradlew.bat --stop
taskkill /F /IM java.exe 2>$null
Start-Sleep -Seconds 3

# Delete build directories
Remove-Item -Recurse -Force shared\build,androidApp\build -ErrorAction SilentlyContinue

# Rebuild
.\gradlew.bat androidApp:assembleDebug
```

**Linux/Mac:**
```bash
./gradlew --stop
pkill -9 java
sleep 2
rm -rf shared/build androidApp/build
./gradlew androidApp:assembleDebug
```

### Solution 3: Delete Specific Locked Files

If you know which file is locked:

**Windows (PowerShell):**
```powershell
# Stop processes
.\gradlew.bat --stop
taskkill /F /IM java.exe 2>$null

# Delete specific directory
Remove-Item -Recurse -Force shared\build\intermediates -ErrorAction SilentlyContinue

# Rebuild
.\gradlew.bat androidApp:assembleDebug
```

## Clean Build Issues

### Problem: "Unable to delete directory"

When running `clean` task, Gradle may fail to delete directories.

**Error:**
```
Execution failed for task ':androidApp:clean'.
> java.io.IOException: Unable to delete directory 'C:\Users\...\build'
```

### Solution: Don't Use Clean

Instead of using `clean`, just rebuild directly:

**❌ Don't do this:**
```powershell
.\gradlew.bat clean androidApp:assembleDebug
```

**✅ Do this instead:**
```powershell
# Just build - Gradle will handle incremental compilation
.\gradlew.bat androidApp:assembleDebug

# If you really need a clean build, delete directories manually first
Remove-Item -Recurse -Force shared\build,androidApp\build -ErrorAction SilentlyContinue
.\gradlew.bat androidApp:assembleDebug
```

## Gradle Daemon Issues

### Problem: Daemon Becomes Unresponsive or Corrupted

Sometimes the Gradle daemon gets into a bad state.

### Solution: Check and Stop Daemons

**Check daemon status:**
```powershell
.\gradlew.bat --status
```

**Stop all daemons:**
```powershell
.\gradlew.bat --stop
```

**Force kill if --stop doesn't work:**
```powershell
taskkill /F /IM java.exe
```

### Problem: "Daemon will be stopped at the end of the build"

This warning indicates daemon issues.

### Solution: Restart Daemon

```powershell
.\gradlew.bat --stop
.\gradlew.bat androidApp:assembleDebug
```

## Build Cache Issues

### Problem: Corrupted Build Cache

Build cache can become corrupted, causing strange build failures.

### Solution: Clear Build Cache

**Windows:**
```powershell
# Stop daemon
.\gradlew.bat --stop

# Clear Gradle cache (be careful - this deletes all cached dependencies)
Remove-Item -Recurse -Force $env:USERPROFILE\.gradle\caches -ErrorAction SilentlyContinue

# Rebuild
.\gradlew.bat androidApp:assembleDebug
```

**Linux/Mac:**
```bash
./gradlew --stop
rm -rf ~/.gradle/caches
./gradlew androidApp:assembleDebug
```

**Note:** This will re-download all dependencies, which can take time.

## Out of Memory Issues

### Problem: "OutOfMemoryError: Java heap space"

Gradle runs out of memory during compilation.

### Solution: Increase Heap Size

Edit `gradle.properties`:

```properties
# Increase Gradle daemon heap size
org.gradle.jvmargs=-Xmx4096m -XX:MaxMetaspaceSize=512m -XX:+HeapDumpOnOutOfMemoryError

# Enable parallel builds
org.gradle.parallel=true

# Enable configuration cache (Gradle 7.0+)
org.gradle.configuration-cache=true
```

## Kotlin Compilation Issues

### Problem: Kotlin compilation takes forever

Kotlin multiplatform compilation can be slow.

### Solution: Use Kotlin Compiler Daemon

In `gradle.properties`:

```properties
# Enable Kotlin compiler daemon
kotlin.compiler.execution.strategy=daemon

# Increase Kotlin daemon heap
kotlin.daemon.jvmargs=-Xmx2048m
```

## IDE Integration Issues

### Problem: IntelliJ IDEA or Android Studio shows errors but Gradle builds fine

IDE and Gradle are out of sync.

### Solution: Sync and Invalidate Caches

1. **Sync Gradle:**
   - File → Sync Project with Gradle Files

2. **Invalidate Caches:**
   - File → Invalidate Caches / Restart
   - Select "Invalidate and Restart"

3. **Reimport Project:**
   - Close project
   - Delete `.idea` folder
   - Reopen project

## Dependency Resolution Issues

### Problem: "Could not resolve dependency"

Network issues or corrupted dependency cache.

### Solution: Refresh Dependencies

```powershell
# Refresh dependencies
.\gradlew.bat --refresh-dependencies androidApp:assembleDebug

# If that doesn't work, clear dependency cache
Remove-Item -Recurse -Force $env:USERPROFILE\.gradle\caches\modules-2 -ErrorAction SilentlyContinue
.\gradlew.bat androidApp:assembleDebug
```

## Best Practices to Avoid Build Issues

### 1. Don't Run Multiple Builds Simultaneously

❌ **Don't:**
- Run multiple Gradle commands at the same time
- Build from both IDE and command line simultaneously

✅ **Do:**
- Wait for one build to complete before starting another
- Use `--stop` between builds if switching contexts

### 2. Close IDE When Running Command Line Builds

❌ **Don't:**
- Keep Android Studio/IntelliJ open while running command line builds

✅ **Do:**
- Close IDE or at least stop any running Gradle tasks in IDE
- Or use IDE's built-in build instead of command line

### 3. Regular Daemon Maintenance

```powershell
# Stop daemon after long development sessions
.\gradlew.bat --stop

# Check daemon status periodically
.\gradlew.bat --status
```

### 4. Use Gradle Wrapper

Always use the Gradle wrapper (`gradlew.bat` or `./gradlew`) instead of system-installed Gradle:

✅ **Correct:**
```powershell
.\gradlew.bat androidApp:assembleDebug
```

❌ **Wrong:**
```powershell
gradle androidApp:assembleDebug
```

## Quick Reference: Common Commands

### Build Commands

```powershell
# Debug build
.\gradlew.bat androidApp:assembleDebug

# Release build
.\gradlew.bat androidApp:assembleRelease

# Install on device
.\gradlew.bat androidApp:installDebug

# Build and install
.\gradlew.bat androidApp:assembleDebug
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

### Troubleshooting Commands

```powershell
# Nuclear option - stop everything and clean
.\gradlew.bat --stop
taskkill /F /IM java.exe 2>$null
Start-Sleep -Seconds 3
Remove-Item -Recurse -Force shared\build,androidApp\build,.gradle -ErrorAction SilentlyContinue
.\gradlew.bat androidApp:assembleDebug

# Check what's running
.\gradlew.bat --status

# Verbose build output
.\gradlew.bat androidApp:assembleDebug --info

# Debug build output
.\gradlew.bat androidApp:assembleDebug --debug

# Build with stacktrace
.\gradlew.bat androidApp:assembleDebug --stacktrace
```

### Testing Commands

```powershell
# Run all tests
.\gradlew.bat shared:testDebugUnitTest

# Run specific test
.\gradlew.bat shared:testDebugUnitTest --tests "com.sshtunnel.data.ServerProfilePropertiesTest"

# Clean and test
Remove-Item -Recurse -Force shared\build\test-results -ErrorAction SilentlyContinue
.\gradlew.bat shared:testDebugUnitTest
```

## When All Else Fails

If you're still having issues after trying everything:

1. **Restart your computer** - Sometimes Windows file locks persist
2. **Check antivirus** - Antivirus can lock files during scanning
3. **Check disk space** - Ensure you have enough free space
4. **Check permissions** - Ensure you have write permissions to project directory
5. **Use a different directory** - Try cloning project to a different location

## Platform-Specific Notes

### Windows

- File locks are more common on Windows than Linux/Mac
- Antivirus software can cause file lock issues
- Use PowerShell instead of CMD for better scripting
- `taskkill /F /IM java.exe` is your friend

### Linux/Mac

- File locks are less common
- Use `pkill -9 java` to force kill Java processes
- Check for zombie processes with `ps aux | grep java`
- Ensure proper file permissions with `chmod`

## Monitoring Build Performance

### Enable Build Scans

Add to `settings.gradle.kts`:

```kotlin
plugins {
    id("com.gradle.enterprise") version "3.15"
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
    }
}
```

Then build with `--scan`:

```powershell
.\gradlew.bat androidApp:assembleDebug --scan
```

This generates a detailed build report showing what's slow.

## Summary

**Most Common Solution:**
```powershell
.\gradlew.bat --stop ; taskkill /F /IM java.exe 2>$null ; Start-Sleep -Seconds 2 ; .\gradlew.bat androidApp:assembleDebug
```

**Nuclear Option:**
```powershell
.\gradlew.bat --stop
taskkill /F /IM java.exe 2>$null
Start-Sleep -Seconds 3
Remove-Item -Recurse -Force shared\build,androidApp\build -ErrorAction SilentlyContinue
.\gradlew.bat androidApp:assembleDebug
```

Remember: Most build issues on Windows are caused by file locks. Stopping the daemon and killing Java processes solves 90% of problems.
