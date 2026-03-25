# SonarQube Local Setup

## First-Time Setup
1. Open `http://localhost:9000`
2. Sign in with `admin / admin`
3. Change the password to `bankflow_sonar`
4. Click `Create Project`
5. Choose `Manually`
6. Set `Project key` to `bankflow`
7. Set the project display name to `BankFlow`
8. Generate a token and export it locally:
   `export SONAR_TOKEN=your-token`

## Quality Gate
Create a quality gate named `BankFlow Gates` with these conditions:
- Coverage is greater than or equal to `80`
- Duplicated Lines (%) is less than `3`
- Reliability Rating is `A`
- Security Rating is `A`
- Maintainability Rating is `A`
- Blocker Issues is equal to `0`
- Critical Issues is equal to `0`

Set `BankFlow Gates` as the default gate for the `bankflow` project.

## Why Aggregate JaCoCo
The dedicated coverage module at [bankflow-coverage-report/pom.xml](/d:/Tutorials/springboot-projects/examples/spring-boot-complete-reference/bankflow-parent/bankflow-coverage-report/pom.xml) runs `jacoco:report-aggregate`.

Why this matters:
- Per-module reports miss cross-module coverage.
- Integration tests in one module can execute code in another module.
- Aggregate coverage gives the real platform-wide number SonarQube should enforce.

## Run Analysis
From repo root:

```bash
./run-sonar.sh
```

Or manually:

```bash
cd bankflow-parent
mvn clean verify sonar:sonar \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=$SONAR_TOKEN
```

## How To Read Results
### Bugs
Red issues mean likely-correctness defects. Fix these before merge.

BankFlow example:
- Risk: null handling in a service method
- Fix: add a null guard before dereferencing an object returned from infrastructure code

### Vulnerabilities
Security issues should be fixed before merge.

BankFlow example:
- Risk: hardcoded secret in configuration
- Fix: read the secret from environment variables like `JWT_SECRET` or `SONAR_TOKEN`

### Code Smells
Maintainability issues can be prioritized over time, but repeated smells become expensive.

BankFlow example:
- Risk: a long service method doing validation, persistence, and event publishing
- Fix: extract smaller private methods such as `validateTransferRequest`, `cacheTransferResponse`, or `recordPaymentCompleted`

### Coverage
SonarQube highlights uncovered lines directly in the file view.

BankFlow example:
- If a failure branch in `PaymentSagaService` is uncovered, add a test that simulates `ACCOUNT_DEBIT_FAILED`
- If account cache miss logic is uncovered, add a test that verifies the repository is called exactly once and the second read is served from cache
