# Task Commit Policy

## Automatic Commits After Task Completion

When executing tasks from any `tasks.md` file in the `.kiro/specs/` directory, you MUST create a Git commit after successfully completing each task.

## Rules

### When to Commit

✅ **ALWAYS commit after:**
- Successfully completing a task from tasks.md
- All tests pass (if the task includes tests)
- Code compiles without errors
- Task status is marked as "completed"

❌ **DO NOT commit if:**
- Task failed or is incomplete
- Tests are failing
- Code has compilation errors
- Task is still in progress

### Commit Message Format

Use the following format for task completion commits:

```
feat(task-<number>): <task title>

<brief description of what was implemented>

Task: <task number and title from tasks.md>
Requirements: <requirement references from task>
```

### Examples

**Example 1: Simple Task**
```
feat(task-1): set up Kotlin Multiplatform project structure

Created shared module with commonMain, commonTest, androidMain, and 
androidTest source sets. Configured Gradle build files for multiplatform.
Set up SQLDelight for cross-platform database. Added core dependencies
including Kotlin Coroutines, Ktor, and kotlinx-serialization.

Task: 1. Set up Kotlin Multiplatform project structure
Requirements: Foundation for all requirements
```

**Example 2: Task with Sub-tasks**
```
feat(task-3): implement shared Profile Repository

Created ProfileRepository interface and implementation using SQLDelight.
Implemented CRUD operations: createProfile, getProfile, getAllProfiles,
updateProfile, deleteProfile. Added error handling with Result types.

Task: 3. Implement shared Profile Repository
Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
```

**Example 3: Task with Property Tests**
```
feat(task-3.1): add property test for profile repository

Implemented property-based test for profile listing completeness.
Test validates that retrieving all profiles returns exactly the
profiles that were saved with correct names and addresses.

Task: 3.1 Write property test for profile repository
Property: 7 - Profile listing completeness
Requirements: 2.2
```

### Commit Workflow

After completing a task, follow this workflow:

1. **Verify Task Completion**
   - All code is written
   - Tests pass (if applicable)
   - No compilation errors
   - Task marked as completed

2. **Stage Changes**
   ```bash
   git add .
   ```

3. **Create Commit**
   ```bash
   git commit -m "feat(task-<number>): <task title>

   <description>

   Task: <task details>
   Requirements: <requirements>"
   ```

4. **Inform User**
   - Tell the user the commit was created
   - Show the commit message
   - Confirm task completion

### Commit Scope Prefixes

Use appropriate prefixes based on task type:

- `feat(task-X)` - New feature implementation (most common)
- `test(task-X)` - Test-only tasks
- `refactor(task-X)` - Refactoring tasks
- `fix(task-X)` - Bug fix tasks
- `docs(task-X)` - Documentation tasks
- `chore(task-X)` - Build/config tasks

### What to Include in Commits

**Always include:**
- All source code changes
- Test files
- Configuration changes
- Documentation updates related to the task

**Exclude:**
- Build outputs (build/, .gradle/)
- IDE files (.idea/, *.iml)
- Temporary files (.kiro/temp/)
- Local configuration (local.properties)

These should already be in .gitignore.

### Multiple Sub-tasks

When a task has multiple sub-tasks:

**Option 1: Commit after each sub-task** (Recommended for large sub-tasks)
```
feat(task-3.1): implement createProfile method
feat(task-3.2): implement getProfile method
feat(task-3.3): implement getAllProfiles method
```

**Option 2: Commit after completing all sub-tasks** (Recommended for small sub-tasks)
```
feat(task-3): implement shared Profile Repository

Completed all sub-tasks:
- 3.1: Created ProfileRepository interface
- 3.2: Implemented CRUD operations
- 3.3: Added error handling

Task: 3. Implement shared Profile Repository
Requirements: 2.1, 2.2, 2.3, 2.4, 2.5
```

Use your judgment based on the size and complexity of sub-tasks.

### Handling Test Tasks

For optional test tasks (marked with `*`):

**If implemented:**
```
test(task-3.1): add property test for profile repository

Task: 3.1 Write property test for profile repository
Property: 7 - Profile listing completeness
Requirements: 2.2
```

**If skipped:**
- No commit needed
- Task remains marked as not started

### Checkpoint Tasks

For checkpoint tasks (e.g., "Ensure all tests pass"):

**If all tests pass:**
```
chore(task-11): checkpoint - all tests passing

Verified all unit tests and property tests pass.
No issues found.

Task: 11. Checkpoint - Ensure all tests pass
```

**If tests fail:**
- Do not commit
- Fix failing tests first
- Then commit with the fixes

### Error Recovery

If you need to fix issues after a commit:

1. **Small fixes:** Amend the previous commit
   ```bash
   git add .
   git commit --amend --no-edit
   ```

2. **Significant changes:** Create a new commit
   ```bash
   git commit -m "fix(task-X): resolve issue with <description>"
   ```

### Benefits of This Policy

✅ **Clear History**: Each task has a corresponding commit
✅ **Easy Rollback**: Can revert to any completed task
✅ **Progress Tracking**: Git log shows task completion timeline
✅ **Code Review**: Each commit is a logical unit of work
✅ **Debugging**: Easy to identify when issues were introduced
✅ **Documentation**: Commit messages document implementation decisions

### Verification

After committing, verify:

```bash
# Check last commit
git log -1

# Check what was committed
git show HEAD

# Verify working directory is clean
git status
```

### Example Session

```
User: "Implement task 2"

Agent: 
1. [Implements task 2]
2. [Runs tests - all pass]
3. [Marks task as completed]
4. [Creates commit]

"Task 2 completed successfully! ✓

Created commit:
feat(task-2): define shared data models and database schema

Created ServerProfile, AppRoutingConfig, and ConnectionSettings data classes.
Defined SQLDelight schema for server_profiles and app_routing_config tables.

Task: 2. Define shared data models and database schema
Requirements: 2.1, 2.2, 2.5, 5.1

All changes have been committed to Git."
```

## Summary

**Golden Rule**: After successfully completing any task from tasks.md, ALWAYS create a Git commit with a descriptive message following the conventional commits format.

This ensures:
- Clean, traceable development history
- Easy rollback to any task completion point
- Clear documentation of what was implemented
- Professional Git workflow
- Better collaboration and code review

## Exceptions

The only time you should NOT commit after a task is if:
- The user explicitly asks not to commit
- The task failed or is incomplete
- Tests are failing
- There are compilation errors

In all other cases, commit after successful task completion.
