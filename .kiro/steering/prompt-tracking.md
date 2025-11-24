---
inclusion: always
---

# Prompt Tracking and History

## Purpose

Maintain a comprehensive history of all user prompts and decisions made during development. This provides context for future work, helps understand the evolution of the project, and serves as documentation for design decisions.

## Prompt History File Location

All prompts and decisions should be tracked in:

```
.kiro/temp/prompts-history.md
```

This file is kept in the temp directory because:
- It's a development artifact, not production code
- It's specific to the development process
- It can grow large over time
- It's useful for context but not part of the final product

## What to Track

### User Prompts

Record every significant user prompt, including:
- Feature requests
- Design discussions
- Requirement changes
- Technical decisions
- Questions and clarifications
- Bug reports
- Refactoring requests

### Decisions Made

Document all major decisions:
- Architecture choices
- Technology selections
- Design patterns adopted
- Features included/excluded
- Scope changes
- Trade-offs made

### Context

For each prompt, include:
- Date and time (session identifier)
- The actual prompt text
- Brief summary of the response/action taken
- Any files created or modified
- Related requirements or tasks

## Format

Use this structure in `prompts-history.md`:

```markdown
# Prompts History

## Session: YYYY-MM-DD - [Session Name]

### [Topic/Phase]

**Prompt:** "[Exact user prompt]"
- Action taken
- Decision made
- Files affected

**Prompt:** "[Next prompt]"
- Action taken
- Decision made

## Key Decisions Made

1. **[Decision Category]**: [Decision and rationale]
2. **[Decision Category]**: [Decision and rationale]

## Files Created/Modified

- `path/to/file.ext` - [Purpose]
- `path/to/another.ext` - [Purpose]

## Next Steps

[What comes next]
```

## When to Update

Update the prompts history file:

✅ **Always**:
- After each user prompt
- When making significant decisions
- When changing requirements or design
- When creating new files or modules
- At the end of each work session

❌ **Don't Update For**:
- Minor typo fixes
- Routine code formatting
- Simple bug fixes (unless they reveal design issues)
- Automated refactoring

## Example Entry

```markdown
## Session: 2025-01-24 - Requirements Phase

### Authentication Method Discussion

**Prompt:** "remove password auth from the auth methods, update prfaq also"
- Removed password authentication support
- Updated to support only private key authentication (RSA, ECDSA, Ed25519)
- Modified PRFAQ.md to reflect key-only authentication
- Modified requirements.md Requirement 3
- Rationale: Stronger security, encourages best practices

**Files Modified:**
- `PRFAQ.md` - Updated authentication section
- `.kiro/specs/ssh-tunnel-proxy/requirements.md` - Updated Requirement 3

**Decision:** Use private key authentication only for better security
```

## Benefits

### For Development

- **Context Preservation**: Understand why decisions were made
- **Onboarding**: New team members can see project evolution
- **Debugging**: Trace when and why features were added/removed
- **Refactoring**: Know what constraints and decisions to respect

### For Documentation

- **Design Rationale**: Document why, not just what
- **Change History**: Track how requirements evolved
- **Decision Log**: Record trade-offs and alternatives considered
- **Learning**: Understand what worked and what didn't

### For AI Assistance

- **Context Window**: Provide relevant history to AI assistants
- **Consistency**: Ensure AI follows previous decisions
- **Continuity**: Maintain project direction across sessions
- **Memory**: Compensate for AI's lack of long-term memory

## Maintenance

### Regular Review

Periodically review the prompts history:
- Monthly: Summarize key decisions
- Quarterly: Archive old sessions
- Annually: Create high-level project timeline

### Archiving

When the file gets too large (>1000 lines):

1. Create archive file:
   ```
   .kiro/temp/prompts-history-archive-YYYY.md
   ```

2. Move old sessions to archive

3. Keep recent sessions (last 3 months) in main file

4. Update main file with link to archives

### Cleanup

Before major releases:
- Review and summarize key decisions
- Move detailed history to archive
- Keep only essential decision log in main file

## Integration with Git

### Commit Messages

Reference prompts history in commit messages when relevant:

```bash
git commit -m "feat(auth): remove password authentication

Removed password auth in favor of key-only authentication
for better security. See prompts-history.md for discussion.

Closes #42"
```

### Pull Requests

Link to relevant prompts history sections in PR descriptions:

```markdown
## Context

This PR implements key-only authentication as discussed in
prompts-history.md (Session 2025-01-24, Authentication Method Discussion).

## Changes
- Removed password authentication
- Updated PRFAQ and requirements
```

## Best Practices

### Be Specific

❌ **Vague**: "User asked to change something"
✅ **Specific**: "User requested removal of password authentication in favor of key-only auth"

### Include Rationale

❌ **No Context**: "Changed to SQLDelight"
✅ **With Context**: "Changed from Room to SQLDelight to support future iOS development with shared database code"

### Link to Files

❌ **No Links**: "Updated requirements"
✅ **With Links**: "Updated `.kiro/specs/ssh-tunnel-proxy/requirements.md` Requirement 3"

### Capture Alternatives

Document alternatives considered:

```markdown
**Decision:** Use Kotlin Multiplatform

**Alternatives Considered:**
- Native Android only (rejected: no iOS support)
- React Native (rejected: performance concerns)
- Flutter (rejected: team expertise in Kotlin)

**Rationale:** KMP allows code sharing while maintaining native performance
```

## Template for New Sessions

```markdown
## Session: YYYY-MM-DD - [Session Name]

### [Topic]

**Prompt:** "[User prompt]"
- [Action taken]
- [Decision made]
- [Files affected]

## Key Decisions Made

1. **[Category]**: [Decision]

## Files Created/Modified

- `path/to/file` - [Purpose]

## Next Steps

[What's next]
```

## Automation

Consider creating a helper script in `.kiro/temp/scripts/`:

```bash
#!/bin/bash
# .kiro/temp/scripts/add-prompt.sh
# Usage: ./add-prompt.sh "User prompt text"

PROMPT="$1"
DATE=$(date +%Y-%m-%d)
TIME=$(date +%H:%M:%S)

echo "" >> .kiro/temp/prompts-history.md
echo "**Prompt ($TIME):** \"$PROMPT\"" >> .kiro/temp/prompts-history.md
echo "- " >> .kiro/temp/prompts-history.md
echo "" >> .kiro/temp/prompts-history.md

echo "Prompt added to history"
```

## Summary

**Golden Rule**: Document every significant prompt and decision in `.kiro/temp/prompts-history.md` to maintain project context and rationale.

This ensures:
- Clear project history
- Documented decision rationale
- Easy onboarding for new contributors
- Context for AI assistants
- Learning from past decisions

## Resources

- [Decision Records](https://adr.github.io/)
- [Project Documentation Best Practices](https://www.writethedocs.org/)
- [Architectural Decision Records](https://cognitect.com/blog/2011/11/15/documenting-architecture-decisions)
