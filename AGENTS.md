# Repository guidance

## Authoritative contract

- Treat `office-seat-bidding-specification.md` as the authoritative functional and security contract.
- Preserve existing bidding and allocation behavior unless the specification requires a change.

## Backend

- Use Java 25 and `mvn25`.
- Keep Jakarta REST and OpenAPI annotations on interfaces in `resource`.
- Put resource implementations in `resource.impl`; do not introduce an `api` package.
- Keep global Quarkus `@ServerExceptionMapper` methods in `resource.ExceptionMappers`.
- Custom application exceptions extend `SeatBiddingException`.
- Use `ConfigurationException` for invalid application configuration.
- Use `ApplicationProblem` only for expected client-facing HTTP failures.
- Unexpected failures must remain logged server errors; do not broadly map `IllegalArgumentException` to HTTP 400.
- Log expected rejected requests at WARN and unexpected or infrastructure failures at ERROR with the exception.
- Never log passwords, activation codes, hashes, cookies, CSRF tokens, or other credentials.

## Persistence and tests

- Use PostgreSQL through Testcontainers for every persistence-related backend test.
- Never substitute H2.
- Use Quarkus `MockMailbox` or another captured fake mailbox in tests; never contact a real SMTP server.
- Inspect the Liquibase history before changing migrations, and never modify a changeset that may already have been applied.
- Run `mvn25 test` after backend changes.
- Keep the application buildable after each coherent change.

## Configuration

- Keep configuration YAML-based.
- Put profile-specific settings in separate files such as `application-dev.yml` and `application-test.yml`.
- Do not use `%dev`, `%test`, or other profile-prefixed keys.
- Do not commit secrets.
- Preserve the configured OpenTelemetry and file-logging behavior unless the specification requires a change.

## Flutter

- Use `seat_bidding` as the Dart package name.
- Use `C:\Tools\flutter\bin\flutter.bat` when that installation is available.
- Run Flutter analysis and tests after client changes.
