# Claude Code Instructions

## Task Master AI Instructions
**Import Task Master's development workflow commands and guidelines, treat as if import is in the main CLAUDE.md file.**
@./.taskmaster/CLAUDE.md

## Context7 MCP Documentation Tool
**Always use Context7 MCP tools when working with libraries, frameworks, or external dependencies.**

When writing code that uses any library or framework:
1. Use `resolve-library-id` to find the correct library identifier first
2. Use `get-library-docs` to retrieve up-to-date documentation and examples
3. Follow the patterns and best practices shown in the retrieved documentation
4. Use specific topics (e.g., 'authentication', 'routing', 'configuration') to focus the documentation search

Examples:
- Before implementing Spring Security: Get docs for Spring Security
- Before using React hooks: Get docs for React with topic 'hooks'  
- Before configuring database connections: Get docs for your ORM/database library

This ensures code follows current best practices and uses the most up-to-date API patterns.
