# Prompts History

This file tracks all user prompts and major decisions made during the development of the SSH Tunnel Proxy project.

## Session: 2025-01-24 - Spec Creation

### Initial Setup
**Prompt:** "read the prfaq doc"
- Read PRFAQ.md to understand the feature requirements

**Prompt:** "let's start"
- Initiated spec creation workflow
- Created initial requirements document with 12 user stories and 60 acceptance criteria

**Prompt:** "initialise a git repo here and commit all work"
- Initialized Git repository
- Committed PRFAQ and requirements document

### Requirements Refinement

**Prompt:** "let's discuss; do you think specifying SOCKS5 is a good choice or is it too limiting?"
- Discussion about SOCKS5 vs other proxy protocols
- Decision: Keep SOCKS5, add note about HTTP proxy for future

**Prompt:** "let's keep it as is, but add to the prfaq and the requirement docs that http proxy will be considered in the future"
- Updated PRFAQ and requirements to mention HTTP proxy as future consideration

**Prompt:** "remove password auth from the auth methods, update prfaq also"
- Removed password authentication
- Updated to support only private key authentication (RSA, ECDSA, Ed25519)

**Prompt:** "req 1 acceptance criteria 1 - ssh key is missing"
- Fixed Requirement 1.1 to explicitly mention private key

**Prompt:** "make reqs 6 and 11 optional, excluded at MVP stage, but keep them in the reqs list"
- Marked Requirement 6 (connection statistics) as Optional - Post-MVP
- Marked Requirement 11 (auto-connect) as Optional - Post-MVP

**Prompt:** "good let's proceed"
- Committed requirements changes
- Moved to design phase

### Design Phase

**Prompt:** "let's discuss: if I need to also create an iphone version of this app in the future, what changes in the design I'd better anticipate?"
- Discussion about iOS support
- Decision: Use Kotlin Multiplatform architecture

**Prompt:** "yes, update the design to anticipate the future refactor if it will be needed. also update requirements if needed"
- Updated design to use Kotlin Multiplatform
- Changed from Room to SQLDelight for cross-platform database
- Added expect/actual patterns for platform-specific code
- Updated technology stack to support both Android and iOS
- Updated requirements to mention platform strategy

**Prompt:** "create a set of best practice Kiro steering documents reflecting the design"
- Created kotlin-multiplatform-architecture.md
- Created ssh-tunnel-security.md
- Created android-vpn-best-practices.md
- Created testing-strategy.md

**Prompt:** "add a steering doc for good git practices"
- Created git-practices.md with conventional commits, branching strategy, PR guidelines

**Prompt:** "first, commit design and requirements documents, then steering documents"
- Committed design.md and requirements.md
- Committed all steering documents

### Development Environment Discussion

**Prompt:** "let's discuss: I want to be able to develop tis app on both my windows and macos machines, switching between them at times. What development environment should i concider?"
- Discussion about cross-platform development setup
- Recommendations for IntelliJ IDEA/Android Studio on both platforms

**Prompt:** "no, don't add this steering document. If I'm only developing in kiro, how the development environment will look? will it help to have a docker dev container?"
- Discussion about Kiro-only development
- Decision: No Docker, just JDK + Android SDK + Kiro

**Prompt:** "ok let's abandon the idea of developing on a mac, i'll be developing only on this machine using kiro"
- Simplified to Windows-only development using Kiro

### Implementation Plan

**Prompt:** "let's discuss: I want to be able to develop tis app on both my windows and macos machines, switching between them at times. What development environment should i concider?" (continued)
- Moved to implementation plan creation

**Prompt:** "Keep optional tasks (faster MVP)"
- Created tasks.md with 29 main tasks and 33 optional property-based test tasks
- Organized into 10 phases from setup to release

### Additional Steering Documents

**Prompt:** "I need an additional steering document that helps keeping the codebase clean. it should specify that any temporary helper scripts or files that kiro needs to create as tooling for coding or implementation, should be kept separate from the project codebase root"
- Created codebase-organization.md
- Specified .kiro/temp/ directory for all temporary files and helper scripts

**Prompt:** "save all the prompts I made so far in a prompts history file and continue doing this in the future (create a steering doc for that too)"
- Created this prompts-history.md file
- Creating prompt-tracking.md steering document

## Key Decisions Made

1. **Authentication**: Private key only (no password auth)
2. **Proxy Protocol**: SOCKS5 (HTTP proxy future consideration)
3. **Architecture**: Kotlin Multiplatform for future iOS support
4. **Database**: SQLDelight (cross-platform)
5. **Development**: Windows + Kiro only (no macOS, no Docker)
6. **MVP Scope**: Requirements 6 and 11 marked as post-MVP
7. **Testing**: Property-based tests marked as optional for faster MVP
8. **Organization**: All temporary files in .kiro/temp/

## Files Created

### Spec Documents
- `.kiro/specs/ssh-tunnel-proxy/requirements.md`
- `.kiro/specs/ssh-tunnel-proxy/design.md`
- `.kiro/specs/ssh-tunnel-proxy/tasks.md`

### Steering Documents
- `.kiro/steering/kotlin-multiplatform-architecture.md`
- `.kiro/steering/ssh-tunnel-security.md`
- `.kiro/steering/android-vpn-best-practices.md`
- `.kiro/steering/testing-strategy.md`
- `.kiro/steering/git-practices.md`
- `.kiro/steering/codebase-organization.md`

### Other
- `PRFAQ.md` (provided by user)
- `.kiro/temp/prompts-history.md` (this file)

## Next Steps

Ready to begin implementation by executing tasks from tasks.md.
