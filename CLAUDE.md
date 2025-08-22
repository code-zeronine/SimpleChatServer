# Claude Code Instructions

## Project Architecture Overview
**SimpleChatServer is a Spring Boot WebFlux-based reactive application**

### Core Technologies:
- **Spring Boot 3.5.4** with **WebFlux** (Reactive Web Framework)
- **Kotlin 1.9.25** with Coroutines
- **R2DBC PostgreSQL** (Reactive Database Connectivity)
- **JWT Authentication** (JJWT 0.12.6)
- **Multi-module Architecture** (api, domain, infrastructure)

### WebFlux-Specific Considerations:
- Use **reactive programming patterns** with `Mono<T>` and `Flux<T>`
- Prefer **functional routing** over annotation-based controllers when appropriate
- Use **WebTestClient** for integration testing
- Configure **ServerHttpSecurity** instead of HttpSecurity
- Use **ServerWebExchange** instead of HttpServletRequest/Response
- Implement **ServerAuthenticationEntryPoint** for reactive error handling

### Development Guidelines:
- Always follow reactive programming principles
- Use `@EnableWebFluxSecurity` for security configuration
- Prefer composition over inheritance in domain models
- Write tests using `StepVerifier` for reactive streams
- Use `@WebFluxTest` for web layer testing

## Task Master AI Instructions
**Import Task Master's development workflow commands and guidelines, treat as if import is in the main CLAUDE.md file.**
@./.taskmaster/CLAUDE.md

## Context7 MCP Documentation Tool
**Always use Context7 MCP tools when working with libraries, frameworks, or external dependencies.**

When writing code that uses any library or framework:
1. Use `resolve-library-id` to find the correct library identifier first
2. Use `get-library-docs` to retrieve up-to-date documentation and examples
3. Follow the patterns and best practices shown in the retrieved documentation
4. Use specific topics (e.g., 'webflux', 'reactive', 'security', 'r2dbc') to focus the documentation search

Examples:
- Before implementing Spring WebFlux Security: Get docs for Spring Security WebFlux
- Before using R2DBC: Get docs for Spring Data R2DBC with topic 'reactive'
- Before configuring WebFlux routing: Get docs for Spring WebFlux with topic 'routing'
- Before implementing reactive authentication: Get docs for Spring Security with topic 'webflux'

This ensures code follows current best practices and uses the most up-to-date reactive API patterns.
