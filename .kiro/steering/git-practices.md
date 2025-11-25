---
inclusion: always
---

# Git Best Practices

## Avoiding Pager Issues in PowerShell

When running git commands in PowerShell (especially in automation or AI agents), git may open a pager (like `less`) that blocks execution and waits for user input. This is problematic for automated workflows.

### Recommended Solution: Use `--no-pager`

The best approach is to use the `--no-pager` flag for commands that might trigger a pager:

```powershell
# View logs without pager
git --no-pager log

# View specific commit
git --no-pager log -1

# View diff
git --no-pager diff

# View file content
git --no-pager show HEAD:path/to/file

# View status (usually doesn't page, but safe to use)
git --no-pager status
```

**Why this is best:**
- ✅ Explicit and clear intent
- ✅ No global state modification
- ✅ Works consistently across all git commands
- ✅ No environment variable management needed
- ✅ Safe for automation and scripting


### Commands That Commonly Trigger Pager

Be especially careful with these commands:
- `git log`
- `git diff`
- `git show`
- `git blame`
- `git branch -v` (with many branches)

### Commands That Usually Don't Page

These are generally safe without `--no-pager`:
- `git status`
- `git add`
- `git commit`
- `git push`
- `git pull`

### Best Practice for Automation

When writing scripts or automation (including AI agents), always use `--no-pager` for any command that displays output:

```powershell
# Good automation practice
git --no-pager log -1 --oneline
git --no-pager status --short
git --no-pager diff --stat
```

## Commit Messages

### Format

Follow the Conventional Commits specification:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

- **feat**: New feature
- **fix**: Bug fix
- **docs**: Documentation changes
- **style**: Code style changes (formatting, no logic change)
- **refactor**: Code refactoring (no feature change or bug fix)
- **perf**: Performance improvements
- **test**: Adding or updating tests
- **chore**: Build process, dependencies, tooling
- **ci**: CI/CD configuration changes

### Examples

```
feat(ssh): add Ed25519 key support

Implement Ed25519 key parsing and authentication to support
modern SSH key formats.

Closes #42
```

```
fix(vpn): prevent DNS leak on network change

When switching between WiFi and mobile data, DNS queries were
briefly using system DNS instead of tunnel DNS.

- Add DNS server validation before routing
- Ensure DNS routes are established before accepting traffic

Fixes #156
```

```
refactor(profile): extract repository interface

Move profile repository to shared module to enable iOS support.
No functional changes.
```

### Commit Message Rules

✅ **Do**:
- Use imperative mood ("add feature" not "added feature")
- Keep subject line under 50 characters
- Capitalize subject line
- Don't end subject with period
- Separate subject from body with blank line
- Wrap body at 72 characters
- Explain what and why, not how
- Reference issues and PRs

❌ **Don't**:
- Write vague messages ("fix bug", "update code")
- Commit unrelated changes together
- Include WIP commits in main branch
- Use past tense
- Write novels in commit messages

## Branching Strategy

### Branch Naming

```
<type>/<issue-number>-<short-description>

Examples:
feature/42-ed25519-support
fix/156-dns-leak
refactor/89-shared-repository
docs/23-update-readme
```

### Main Branches

- **main**: Production-ready code, always stable
- **develop**: Integration branch for features (optional, for larger teams)

### Supporting Branches

- **feature/**: New features
- **fix/**: Bug fixes
- **hotfix/**: Urgent production fixes
- **refactor/**: Code refactoring
- **docs/**: Documentation updates

### Branch Lifecycle

```bash
# Create feature branch from main
git checkout main
git pull origin main
git checkout -b feature/42-ed25519-support

# Work on feature, commit regularly
git add .
git commit -m "feat(ssh): add Ed25519 key parsing"

# Keep branch updated with main
git checkout main
git pull origin main
git checkout feature/42-ed25519-support
git rebase main

# Push to remote
git push origin feature/42-ed25519-support

# Create pull request
# After review and approval, merge to main
# Delete feature branch
git branch -d feature/42-ed25519-support
git push origin --delete feature/42-ed25519-support
```

## Pull Requests

### PR Title

Use same format as commit messages:

```
feat(ssh): add Ed25519 key support
fix(vpn): prevent DNS leak on network change
```

### PR Description Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Changes Made
- Bullet point list of changes
- Be specific and clear

## Testing
- [ ] Unit tests added/updated
- [ ] Property tests added/updated
- [ ] Integration tests pass
- [ ] Manual testing completed

## Checklist
- [ ] Code follows project style guidelines
- [ ] Self-review completed
- [ ] Comments added for complex code
- [ ] Documentation updated
- [ ] No new warnings
- [ ] Tests pass locally

## Related Issues
Closes #42
Related to #38
```

### PR Best Practices

✅ **Do**:
- Keep PRs small and focused (< 400 lines changed)
- Write clear description
- Add screenshots for UI changes
- Request specific reviewers
- Respond to review comments promptly
- Update PR based on feedback
- Squash commits before merging (if needed)

❌ **Don't**:
- Create massive PRs (> 1000 lines)
- Mix unrelated changes
- Force push after review started
- Merge without approval
- Leave unresolved comments
- Ignore CI failures

## Code Review

### As a Reviewer

✅ **Do**:
- Review within 24 hours
- Be constructive and respectful
- Explain reasoning for suggestions
- Approve if changes are good enough
- Test locally if needed
- Check for security issues
- Verify tests are adequate

❌ **Don't**:
- Nitpick on style (use linters)
- Block on personal preferences
- Approve without reading
- Be vague in comments
- Demand perfection

### Review Comments

```markdown
# Good comments

**Suggestion**: Consider using `when` expression here for better readability.

**Question**: What happens if the SSH connection drops during this operation?

**Security**: This could expose the private key in logs. Please sanitize before logging.

**Nit**: Typo in variable name: `conection` → `connection`

# Bad comments

"This is wrong" (not helpful)
"Change this" (no explanation)
"I would do it differently" (not constructive)
```

## Commit Hygiene

### Atomic Commits

Each commit should be a single logical change:

```bash
# Good: Separate commits for separate concerns
git commit -m "feat(ssh): add Ed25519 key parsing"
git commit -m "test(ssh): add tests for Ed25519 keys"
git commit -m "docs(ssh): document Ed25519 support"

# Bad: Everything in one commit
git commit -m "add Ed25519 support with tests and docs"
```

### Amending Commits

```bash
# Fix last commit (before pushing)
git add forgotten-file.kt
git commit --amend --no-edit

# Change last commit message
git commit --amend -m "feat(ssh): add Ed25519 key support"
```

### Interactive Rebase

```bash
# Clean up last 3 commits before pushing
git rebase -i HEAD~3

# In editor:
# pick abc123 feat(ssh): add Ed25519 parsing
# squash def456 fix typo
# squash ghi789 fix tests
```

## Merge Strategies

### Squash and Merge (Recommended)

Combines all commits into one clean commit:

```bash
git checkout main
git merge --squash feature/42-ed25519-support
git commit -m "feat(ssh): add Ed25519 key support"
```

**Pros**: Clean history, easy to revert
**Cons**: Loses individual commit history

### Rebase and Merge

Replays commits on top of main:

```bash
git checkout feature/42-ed25519-support
git rebase main
git checkout main
git merge --ff-only feature/42-ed25519-support
```

**Pros**: Linear history, preserves commits
**Cons**: Can be complex with conflicts

### Merge Commit (Avoid)

Creates a merge commit:

```bash
git checkout main
git merge feature/42-ed25519-support
```

**Pros**: Preserves full history
**Cons**: Cluttered history with merge commits

## Git Workflow

### Daily Workflow

```bash
# Start of day: Update main
git checkout main
git pull origin main

# Create feature branch
git checkout -b feature/42-new-feature

# Work and commit regularly
git add .
git commit -m "feat: implement X"

# Before pushing: Update with main
git fetch origin main
git rebase origin/main

# Push to remote
git push origin feature/42-new-feature

# Create PR and wait for review
# After approval: Squash and merge via GitHub/GitLab
```

### Handling Conflicts

```bash
# During rebase
git rebase main
# CONFLICT in file.kt

# Fix conflicts in editor
# Then:
git add file.kt
git rebase --continue

# If stuck:
git rebase --abort
```

## Git Configuration

### User Setup

```bash
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"
```

### Useful Aliases

```bash
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit
git config --global alias.st status
git config --global alias.unstage 'reset HEAD --'
git config --global alias.last 'log -1 HEAD'
git config --global alias.visual 'log --oneline --graph --decorate --all'
```

### Editor Setup

```bash
# Use VS Code as default editor
git config --global core.editor "code --wait"

# Use Vim
git config --global core.editor "vim"
```

## .gitignore

### Android Project

```gitignore
# Android
*.apk
*.ap_
*.dex
*.class
bin/
gen/
out/
build/
.gradle/
local.properties

# IDE
.idea/
*.iml
.vscode/
*.swp
*.swo
*~

# OS
.DS_Store
Thumbs.db

# Secrets
*.keystore
*.jks
secrets.properties
google-services.json

# Logs
*.log

# Temporary
*.tmp
*.bak
```

## Git Hooks

### Pre-commit Hook

```bash
#!/bin/sh
# .git/hooks/pre-commit

# Run linter
./gradlew ktlintCheck
if [ $? -ne 0 ]; then
    echo "Linting failed. Please fix errors before committing."
    exit 1
fi

# Run tests
./gradlew testDebugUnitTest
if [ $? -ne 0 ]; then
    echo "Tests failed. Please fix before committing."
    exit 1
fi
```

### Commit Message Hook

```bash
#!/bin/sh
# .git/hooks/commit-msg

commit_msg=$(cat "$1")

# Check format: type(scope): subject
if ! echo "$commit_msg" | grep -qE "^(feat|fix|docs|style|refactor|perf|test|chore|ci)(\(.+\))?: .+"; then
    echo "Error: Commit message doesn't follow conventional commits format"
    echo "Format: type(scope): subject"
    echo "Example: feat(ssh): add Ed25519 support"
    exit 1
fi
```

## Troubleshooting

### Undo Last Commit (Not Pushed)

```bash
# Keep changes
git reset --soft HEAD~1

# Discard changes
git reset --hard HEAD~1
```

### Undo Pushed Commit

```bash
# Create revert commit
git revert HEAD
git push origin main
```

### Recover Deleted Branch

```bash
# Find commit hash
git reflog

# Recreate branch
git checkout -b feature/42-recovered <commit-hash>
```

### Clean Working Directory

```bash
# Remove untracked files
git clean -fd

# Remove ignored files too
git clean -fdx
```

## Resources

- [Conventional Commits](https://www.conventionalcommits.org/)
- [Git Best Practices](https://git-scm.com/book/en/v2)
- [GitHub Flow](https://guides.github.com/introduction/flow/)
- [Atlassian Git Tutorials](https://www.atlassian.com/git/tutorials)
