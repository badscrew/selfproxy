---
inclusion: always
---

# Codebase Organization and Cleanliness

## Project Structure

Keep the project root clean and organized. Only production code, configuration, and essential project files should exist in the main codebase.

### Root Directory Structure

```
ssh-tunnel-proxy/
├── .git/                    # Git repository
├── .gitignore              # Git ignore rules
├── .kiro/                  # Kiro configuration and specs
├── shared/                 # Kotlin Multiplatform shared code
├── androidApp/             # Android application code
├── gradle/                 # Gradle wrapper
├── build.gradle.kts        # Root build configuration
├── settings.gradle.kts     # Gradle settings
├── gradlew                 # Gradle wrapper script (Unix)
├── gradlew.bat             # Gradle wrapper script (Windows)
├── README.md               # Project documentation
├── LICENSE                 # License file
└── local.properties        # Local configuration (gitignored)
```

## Temporary Files and Helper Scripts

### Where to Put Temporary Files

**NEVER** place temporary files, helper scripts, or tooling in the project root or source directories.

Instead, use a dedicated directory:

```
.kiro/temp/                 # Temporary files and helper scripts
├── scripts/                # Helper scripts for development
├── tools/                  # Temporary tooling
├── generated/              # Generated files for testing
└── scratch/                # Scratch files and experiments
```

### Rules for Temporary Files

✅ **Do**:
- Create `.kiro/temp/` directory for all temporary files
- Use descriptive names for temporary files
- Document the purpose of helper scripts
- Clean up temporary files when no longer needed
- Add `.kiro/temp/` to `.gitignore`

❌ **Don't**:
- Place temporary files in project root
- Mix temporary files with source code
- Commit temporary files to Git
- Leave undocumented helper scripts
- Create random files in source directories

### Examples

**Bad - Clutters project root:**
```
ssh-tunnel-proxy/
├── test_script.py          # ❌ Temporary script in root
├── helper.sh               # ❌ Helper script in root
├── output.txt              # ❌ Generated file in root
├── temp_data.json          # ❌ Temporary data in root
└── shared/
    └── src/
        └── test.kt         # ❌ Test file in source
```

**Good - Organized in .kiro/temp/:**
```
ssh-tunnel-proxy/
├── .kiro/
│   └── temp/
│       ├── scripts/
│       │   ├── test_ssh_connection.py
│       │   └── generate_test_keys.sh
│       ├── tools/
│       │   └── key_format_validator.kt
│       └── generated/
│           ├── test_output.txt
│           └── sample_data.json
└── shared/
    └── src/
        └── commonMain/
            └── kotlin/
```

## .gitignore Configuration

Ensure temporary directories are ignored:

```gitignore
# Kiro temporary files
.kiro/temp/

# Build outputs
build/
.gradle/
*.apk
*.aab

# IDE
.idea/
*.iml
.vscode/

# Local configuration
local.properties

# OS files
.DS_Store
Thumbs.db

# Logs
*.log
```

## Helper Scripts Guidelines

### When Creating Helper Scripts

If you need to create helper scripts for:
- Testing SSH connections
- Generating test data
- Validating configurations
- Running experiments
- Debugging issues

**Always place them in `.kiro/temp/scripts/`**

### Script Documentation

Each helper script should include a header comment:

```python
#!/usr/bin/env python3
"""
Purpose: Test SSH connection with various key formats
Usage: python test_ssh_connection.py <hostname> <key_file>
Created: 2025-01-15
Note: Temporary script for development - not part of production code
"""
```

```bash
#!/bin/bash
# Purpose: Generate test SSH keys for all supported formats
# Usage: ./generate_test_keys.sh
# Created: 2025-01-15
# Note: Temporary script for development - not part of production code
```

## Source Code Organization

### Shared Module (Kotlin Multiplatform)

```
shared/
├── build.gradle.kts
└── src/
    ├── commonMain/
    │   ├── kotlin/
    │   │   └── com/
    │   │       └── sshtunnel/
    │   │           ├── data/          # Data models
    │   │           ├── domain/        # Business logic
    │   │           ├── repository/    # Data access
    │   │           └── util/          # Utilities
    │   └── sqldelight/
    │       └── com/sshtunnel/
    │           └── db/
    ├── commonTest/
    │   └── kotlin/
    │       └── com/sshtunnel/
    ├── androidMain/
    │   └── kotlin/
    │       └── com/sshtunnel/
    │           ├── ssh/               # Android SSH client
    │           ├── vpn/               # Android VPN provider
    │           └── storage/           # Android credential store
    └── androidTest/
        └── kotlin/
            └── com/sshtunnel/
```

### Android App Module

```
androidApp/
├── build.gradle.kts
└── src/
    └── main/
        ├── kotlin/
        │   └── com/
        │       └── sshtunnel/
        │           ├── ui/            # Compose UI
        │           │   ├── screens/
        │           │   ├── components/
        │           │   └── theme/
        │           ├── di/            # Dependency injection
        │           ├── service/       # VPN service
        │           └── MainActivity.kt
        ├── res/                       # Android resources
        └── AndroidManifest.xml
```

## File Naming Conventions

### Source Files

- Use PascalCase for class files: `ProfileRepository.kt`
- Use camelCase for file-level functions: `utils.kt`
- Use descriptive names: `SSHConnectionManager.kt` not `Manager.kt`

### Test Files

- Match source file name with `Test` suffix: `ProfileRepositoryTest.kt`
- Property tests: `ProfilePropertiesTest.kt`
- Integration tests: `SSHConnectionIntegrationTest.kt`

### Configuration Files

- Use lowercase with hyphens: `build.gradle.kts`
- Use descriptive names: `local.properties` not `config.properties`

## Code Organization Within Files

### File Structure

```kotlin
// 1. Package declaration
package com.sshtunnel.domain

// 2. Imports (grouped and sorted)
import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

// 3. File-level documentation
/**
 * Manages SSH connections and SOCKS5 proxy creation.
 */

// 4. Constants (if any)
private const val DEFAULT_PORT = 22

// 5. Main class/interface
interface SSHConnectionManager {
    // Public API first
    suspend fun connect(profile: ServerProfile): Result<Connection>
    
    // Then internal/private
}

// 6. Data classes and sealed classes
data class Connection(
    val sessionId: String,
    val socksPort: Int
)

// 7. Extension functions (if any)
```

## Cleanup Checklist

Before committing code:

- [ ] No temporary files in project root
- [ ] No commented-out code blocks
- [ ] No debug print statements
- [ ] No unused imports
- [ ] No TODO comments without issue references
- [ ] All helper scripts in `.kiro/temp/`
- [ ] All generated files in `.kiro/temp/generated/`
- [ ] `.gitignore` is up to date

## Automated Cleanup

### Pre-commit Hook

Consider adding a pre-commit hook to prevent temporary files from being committed:

```bash
#!/bin/sh
# .git/hooks/pre-commit

# Check for temporary files in wrong locations
if git diff --cached --name-only | grep -E "^(test_|temp_|helper_|scratch_)"; then
    echo "Error: Temporary files detected in commit"
    echo "Please move temporary files to .kiro/temp/"
    exit 1
fi

# Check for debug statements
if git diff --cached | grep -E "(console\.log|println|print\(|debugger)"; then
    echo "Warning: Debug statements detected"
    echo "Please remove debug statements before committing"
    exit 1
fi
```

## Documentation Files

### Where Documentation Belongs

- **Project README**: Root directory (`README.md`)
- **API Documentation**: Generated in `docs/` directory
- **User Guides**: `docs/guides/` directory
- **Architecture Docs**: `.kiro/specs/` directory
- **Temporary Notes**: `.kiro/temp/notes/` directory

### Documentation Structure

```
docs/
├── api/                    # Generated API docs
├── guides/                 # User guides
│   ├── setup.md
│   ├── key-generation.md
│   └── troubleshooting.md
└── architecture/           # Architecture diagrams
    └── system-overview.png
```

## Build Artifacts

### Where Build Outputs Go

All build outputs should be in `build/` directories:

```
shared/build/               # Shared module build outputs
androidApp/build/           # Android app build outputs
.gradle/                    # Gradle cache
```

These directories should be in `.gitignore` and never committed.

## Summary

**Golden Rule**: Keep the project root and source directories clean. All temporary files, helper scripts, and tooling go in `.kiro/temp/`.

This ensures:
- Clean Git history
- Easy navigation
- Professional codebase
- Clear separation of concerns
- No accidental commits of temporary files

## Resources

- [Kotlin Coding Conventions](https://kotlinlang.org/docs/coding-conventions.html)
- [Android Project Structure](https://developer.android.com/studio/projects)
- [Clean Code Principles](https://www.amazon.com/Clean-Code-Handbook-Software-Craftsmanship/dp/0132350882)
