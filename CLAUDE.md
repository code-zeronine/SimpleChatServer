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

## Project Structure Guidelines
**Always follow the established project structure and architectural patterns when implementing code.**

### Multi-Module Architecture:
```
SimpleChatServer/
├── api/          # Application layer (Controllers, Services, DTOs)
├── domain/       # Domain layer (Entities, Value Objects, Domain Logic)
└── infrastructure/  # Infrastructure layer (Repositories, External APIs, Data Persistence)
```

### Implementation Rules by Module:

#### **API Module (`/api`)**
- **Controllers**: REST API endpoints, WebSocket handlers
- **Application Services**: Business workflow orchestration, transaction boundaries
- **DTOs**: Request/Response data transfer objects
- **Configuration**: Web security, CORS, validation
- **Dependencies**: Can depend on domain and infrastructure modules

Examples:
- `ChatRoomController` - REST API endpoints
- `ChatMessageService` - Application service orchestrating business workflows
- `CreateChatRoomRequest` - Request DTO

#### **Domain Module (`/domain`)**
- **Entities**: Core business objects (ChatMessage, User, ChatRoom)
- **Value Objects**: Immutable objects representing concepts
- **Domain Logic**: Business rules and validation
- **Interfaces**: Repository interfaces (not implementations)
- **Dependencies**: Should have NO dependencies on other modules

Examples:
- `ChatMessage` - Pure domain entity with business logic
- `MessageType` - Enum/Value object
- `ChatMessageRepository` - Interface only

#### **Infrastructure Module (`/infrastructure`)**
- **Repository Implementations**: Data access layer
- **Entity Classes**: Database-specific entities (R2DBC, MongoDB)
- **External Service Adapters**: Third-party integrations
- **Configuration**: Database, messaging, external services
- **Dependencies**: Can depend on domain module only

Examples:
- `ChatMessageEntity` - MongoDB document entity
- `ChatMessageRepositoryImpl` - Repository implementation
- `MongoConfig` - Database configuration

### Code Implementation Guidelines:

1. **Service Placement**:
   - Application Services → `api/src/main/kotlin/com/simplechat/service/`
   - Domain Services → `domain/src/main/kotlin/com/simplechat/domain/service/`

2. **Entity Separation**:
   - Domain Entities → `domain/src/main/kotlin/com/simplechat/domain/entity/`
   - Infrastructure Entities → `infrastructure/src/main/kotlin/com/simplechat/infrastructure/entity/`

3. **Repository Pattern**:
   - Interfaces → `domain/src/main/kotlin/com/simplechat/domain/repository/`
   - Implementations → `infrastructure/src/main/kotlin/com/simplechat/infrastructure/repository/`

4. **Clean Architecture Principles**:
   - Domain layer should be free of external dependencies
   - Infrastructure entities should convert to/from domain entities
   - Application services should orchestrate domain logic and coordinate with infrastructure

5. **Testing Strategy**:
   - Unit tests for domain logic using pure objects
   - Application service tests using MockK for dependencies
   - Integration tests for repository implementations
   - Web layer tests using `@WebFluxTest`

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
