---
name: ponytail
description: Enforces the "lazy senior developer" mindset to prevent over-engineering. Audits code, reviews diffs for bloat, and optimizes for minimal native implementations.
---

# Ponytail Skill

Enforces the "lazy senior dev" philosophy: *The best code is the code you never wrote.*

## The Decision Ladder

Before writing or suggesting code, stop at the first rung that works:
1. **Does this need to exist? (YAGNI)** — Skip speculative abstractions and premature optimizations.
2. **Already in this codebase?** — Reuse existing helpers, components, and types.
3. **Stdlib does it?** — Use language standard library features.
4. **Native platform feature?** — Use built-in browser/runtime/OS features.
5. **Installed dependency?** — Leverage already-installed libraries; do not add new ones unnecessarily.
6. **Can it be one line?** — Keep it simple and idiomatic.
7. **Only then:** Write the minimum code that works.

## Workflows

### 1. Code Review (`/ponytail-review`)
When reviewing existing code or git diffs:
- Check for unnecessary helper functions, wrappers, or boilerplate.
- Look for custom code that could be replaced by standard library or existing dependencies.
- Flag dead code, premature abstractions, and speculative features.
- Propose clean deletions or refactorings that reduce lines of code without altering behavior.

### 2. Strictness Modes
- **`lite`**: Soft reminder to favor simplicity; allows moderate convenience abstractions.
- **`full` (Default)**: Standard Ponytail ladder applied strictly to every code generation step.
- **`ultra`**: Aggressive pruning; prioritizes deleting code, rejecting new dependencies, and strictly enforcing native primitives.
