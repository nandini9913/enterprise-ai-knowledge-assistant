# Step 1: Spring Boot Foundation

## What we built
A minimal Spring Boot 3 application running on Java 17 with one health endpoint.

## Why Spring Boot
Spring Boot gives us auto-configuration, dependency management, an embedded web server, and a standard application structure so we can focus on business logic.

## What happens at startup
1. The JVM starts `KnowledgeAssistantApplication.main()`.
2. `SpringApplication.run()` creates the Spring ApplicationContext.
3. `@SpringBootApplication` enables component scanning and auto-configuration.
4. Spring detects `HealthController` as a REST controller.
5. The embedded server starts on port 8080.
6. A request to `/api/v1/health` is routed to `HealthController.health()`.

## Interview questions
- What does `@SpringBootApplication` contain?
- What is dependency injection?
- What is the difference between `@Controller` and `@RestController`?
- What is Spring Boot auto-configuration?
- Why should configuration live outside Java code?

Next: PostgreSQL and document metadata.
