# ADR-0002: Target Spring Boot 4.1 for the planning service

- **Status:** Accepted
- **Date:** 2026-10-23

## Context
The Week 0 scaffold was generated against Spring Boot 3.5.x. On checking spring.io, the current
GA release is 4.1.2, and new code should start on a supported line rather than inherit an
upgrade later.

## Options considered
1. **Stay on 3.5.x**: no code changes now, but we'd be starting a new service on an older
   major version and owe a migration later.
2. **Move to 4.1.x now**: a few mechanical changes while the codebase is tiny.

## Decision
Move to Spring Boot 4.1.2 now. Changes required:
- `spring-boot-starter-web` -> `spring-boot-starter-webmvc` (Boot 4 starter naming).
- Add `spring-boot-starter-webmvc-test`; Boot 4 splits test auto-configuration per technology.
- `@AutoConfigureMockMvc` moved to `org.springframework.boot.webmvc.test.autoconfigure`.

## Consequences
Upgrading at ~2 files is far cheaper than later. Some older tutorials/StackOverflow answers use
the 3.x package names, so follow the 4.x reference docs.
