# Repository instructions

- Do not commit private IP addresses, device serial numbers, credentials,
  signing keys, passwords, or other secrets.
- Keep Android signing files outside the repository. Use GitHub Actions
  secrets for release signing.
- Use Semantic Versioning tags in the form `vMAJOR.MINOR.PATCH`.
- Run `make lint`, `./gradlew testDebugUnitTest`, and `make build` before each
  pull request.
- Keep the default `KEY_CHAT` mapping compatible with Projectivy Launcher.
