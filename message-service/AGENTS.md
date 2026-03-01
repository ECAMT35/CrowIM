# Repository Guidelines

## Scope

This guide applies only to the `message-service` module.
Do not assume monorepo-wide changes.

---

## Mandatory Output Workflow

Always follow this two-phase structure:

1) Plan
    - Brief idea
    - Files to modify
    - Step-by-step change plan

2) Execute
    - Then implement changes

Do NOT modify code before providing the Plan.

---

## Change Strategy

- Prefer the smallest possible change.
- Do NOT refactor unless explicitly required.
- Do NOT introduce unrelated optimizations.
- Do NOT modify build, config, or dependencies unless necessary.
- Only touch files directly related to the requested feature.

---

## Dependency Injection Rules

- Prefer constructor injection.
- Use `@RequiredArgsConstructor` by default.
- `@Resource` may be used only when necessary.
- Avoid field injection.

---

## Engineering & Architecture Principles

- Keep code production-grade and maintainable.
- Follow mainstream Spring Boot engineering practices.
- Prioritize clarity over cleverness.
- Prefer simple, high-performance solutions.
- Avoid unnecessary abstraction layers.

---

## Comment & Logging Rules

### Comments

- Write comments in Chinese.
- Add comments at key logic points explaining what the code does.
- For complex methods, add a short JavaDoc-style header:

  /**
    * 简单功能介绍
    *
    * 参数：
    * @param xxx 参数说明
    *
    * 返回值：
    * @return 返回值说明
      */

- `@param`：仅在方法有入参时填写；`@return`：仅在方法返回非 `void` 时填写。
- Do NOT convert explanatory comments into logs.

### Logging

- Use `@Slf4j` when meaningful.
- Log success or failure at important boundaries only.
- Error logs must include sufficient context.
- Log content must be in English.
- Never log sensitive data.

---

## Token & Context Control

- Prefer reading only files explicitly mentioned.
- Do not scan the whole module unless necessary.
- Never read `target/`, compiled classes, or large logs.
- Avoid broad repository searches unless justified.