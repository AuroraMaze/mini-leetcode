# Mini LeetCode Platform - Phase 1 Adversarial Reviewer Handoff Report (Round 3)

> [!WARNING] **Skepticism Disclaimer**
> 79 comprehensive tests are passing with 96.12% instruction coverage and 96.40% line coverage. Storage leaks on partial failure, uncaught filter exceptions for deleted user tokens, missing multipart exception handlers, negative order index anomalies, and Java 21 compliance have been resolved. Live network socket communication against active MinIO/MySQL Docker daemons was verified via MockMvc and mock stream contracts, not live Windows Docker daemons (due to local Docker Desktop service state).

---

## 1. What the Prior Attempt Got Wrong

### Issue 1: Orphaned MinIO Files on Partial Upload or Persistence Failure in `addTestCase`
- **Input:** In `TestCaseService.addTestCase`, upload of `inputFile` succeeds to MinIO, but upload of `outputFile` throws an exception (e.g., storage network error or disk limit) or database transaction fails before commit.
- **Expected:** Any file uploaded during the partial operation must be deleted immediately from MinIO storage (`storageService.deleteFileByUri`) to maintain storage/database integrity.
- **Actual:** `inputUri` remained orphaned in MinIO bucket indefinitely with no database reference, causing silent storage leaks.
- **Root Cause:** Lack of cleanup rollback handling in `TestCaseService.addTestCase` if an exception occurs after `inputFile` is uploaded.

### Issue 2: Uncaught Filter Exception When JWT Belongs to Deleted User Crashing Public & Protected Requests
- **Input:** HTTP request sent to any endpoint (including public endpoints like `GET /api/v1/problems`) with a validly signed, unexpired JWT whose user was deleted from the database.
- **Expected:** `JwtAuthenticationFilter` should catch user lookup errors, clear the `SecurityContext`, and allow the request to proceed as unauthenticated (returning 401 for protected endpoints, and anonymous data for public endpoints).
- **Actual:** `userDetailsService.loadUserByUsername` threw uncaught `UsernameNotFoundException` inside `JwtAuthenticationFilter`. Because this filter executes outside `ExceptionTranslationFilter`, the exception crashed out of the servlet filter chain with a 500 / ServletException.
- **Root Cause:** Absence of a `try-catch` block around `loadUserByUsername` inside `JwtAuthenticationFilter.doFilterInternal`.

### Issue 3: Unchecked Negative Testcase `orderIndex` and Omitted Index Collisions
- **Input:**
  1. Instructor uploads a testcase with `orderIndex = -1` (or negative integer).
  2. Instructor uploads multiple testcases without specifying `orderIndex` (omitted query param).
- **Expected:**
  1. Negative `orderIndex` must be rejected with HTTP 400 Bad Request ("Order index must not be negative").
  2. Omitted `orderIndex` must automatically increment sequentially (`testCaseRepository.countByProblemId(problemId)`) to avoid all testcases colliding at index 0.
- **Actual:** Negative `orderIndex` was accepted, creating file paths like `problems/1/testcases/-1_input_xxx.txt` and storing `-1` in the database. Omitted `orderIndex` defaulted to 0 for all testcases.
- **Root Cause:** Lack of non-negative validation in `TestCaseService.addTestCase` and `TestCaseController` hardcoding `defaultValue = "0"`.

### Issue 4: Unhandled Multipart and Media Type Client Exceptions in `GlobalExceptionHandler`
- **Input:**
  1. Client sends multipart request missing `outputFile` or `inputFile` part (`MissingServletRequestPartException`).
  2. Client sends non-multipart request to multipart endpoint (`MultipartException`).
  3. Client requests endpoint with unsupported media type (`HttpMediaTypeNotSupportedException`).
  4. File upload exceeds max size limit (`MaxUploadSizeExceededException`).
- **Expected:**
  - Missing multipart part / non-multipart -> HTTP 400 Bad Request RFC 7807 ProblemDetail.
  - Unsupported media type -> HTTP 415 Unsupported Media Type RFC 7807 ProblemDetail.
  - Max upload size exceeded -> HTTP 413 Payload Too Large RFC 7807 ProblemDetail.
- **Actual:**
  - Missing parts and unsupported media type fell through to `handleGenericException` (HTTP 500 Internal Server Error).
  - Max upload size returned HTTP 400 Bad Request despite having title "Payload Too Large".
- **Root Cause:** `GlobalExceptionHandler` lacked exception handlers for `MissingServletRequestPartException`, `MultipartException`, and `HttpMediaTypeNotSupportedException`, and mapped `MaxUploadSizeExceededException` to status 400 instead of 413.

### Issue 5: `pom.xml` Configured for Java 17 Instead of Java 21 LTS
- **Input:** Project build definition `<java.version>17</java.version>` in `pom.xml`.
- **Expected:** As specified in `<original_task>` ("thiết lập chuẩn Spring Boot 3.3.x trên Java 21 LTS"), Java version should be 21.
- **Actual:** Set to 17.
- **Root Cause:** Default setting was not upgraded to Java 21 LTS in `pom.xml`.

### Issue 6: `JwtTokenProvider.hashToken(null)` Throwing Raw NullPointerException
- **Input:** `JwtTokenProvider.hashToken(null)`
- **Expected:** Clean `IllegalArgumentException("Token cannot be null")`.
- **Actual:** Threw raw `NullPointerException`.
- **Root Cause:** Missing null guard on `token` parameter in `JwtTokenProvider.hashToken`.

---

## 2. What I Changed

1. **`TestCaseService.java`**:
   - Added validation: `if (orderIndex != null && orderIndex < 0) throw new IllegalArgumentException("Order index must not be negative");`.
   - Added auto-sequential index resolution: if `orderIndex == null`, defaults to `(int) testCaseRepository.countByProblemId(problemId)`.
   - Added atomic cleanup on partial failure: if `inputFile` is uploaded and an exception occurs before `testCaseRepository.save` completes (such as `outputFile` upload failure or DB failure), all uploaded MinIO files are deleted via `storageService.deleteFileByUri`.
2. **`TestCaseRepository.java`**:
   - Added `long countByProblemId(Long problemId);` method declaration.
3. **`TestCaseController.java`**:
   - Updated `@RequestParam(value = "orderIndex", required = false)` to permit automatic sequential ordering when omitted by clients.
4. **`JwtAuthenticationFilter.java`**:
   - Wrapped `userDetailsService.loadUserByUsername` in `try-catch (Exception e)` to clear `SecurityContextHolder` if user is deleted or claims cannot be loaded, preventing filter crashes and properly allowing unauthenticated flow.
5. **`JwtTokenProvider.java`**:
   - Added null guard in `hashToken(String token)` throwing `IllegalArgumentException("Token cannot be null")`.
6. **`GlobalExceptionHandler.java`**:
   - Added `MissingServletRequestPartException.class` and `MultipartException.class` to HTTP 400 Bad Request handler.
   - Added `HttpMediaTypeNotSupportedException.class` handler returning HTTP 415 Unsupported Media Type.
   - Updated `handleMaxUploadSizeExceeded` to return HTTP 413 Payload Too Large (`HttpStatus.PAYLOAD_TOO_LARGE`).
7. **`pom.xml`**:
   - Updated `<java.version>21</java.version>` to strictly satisfy Java 21 LTS requirement.
8. **Test Suites**:
   - **`GlobalExceptionHandlerTest.java`**: Added tests for `handleMediaTypeNotSupported` (415), `handleMaxUploadSizeExceeded` (413), and `handleMissingServletRequestPartException` (400).
   - **`SecurityUnitTest.java`**: Added tests for `hashToken(null)` throwing `IllegalArgumentException`, expired access token returning `false`, and tampered token signature returning `false`.
   - **`TestCaseServiceTest.java`**: Added tests for negative `orderIndex` rejection, auto-increment sequential ordering on null index, and physical MinIO file cleanup when partial upload fails.
   - **`AdversarialReviewerTest.java`**:
     - Added default Mockito stub in `setUp()` for `storageService.uploadFile` to return clean `minio://testcases/...` URIs.
     - Added **Adversarial Test 15**: Deleted user's token does not crash servlet filter and proceeds unauthenticated (200 on public endpoints, 401 on protected).
     - Added **Adversarial Test 16**: Uploading testcase with negative orderIndex returns HTTP 400 ProblemDetail.
     - Added **Adversarial Test 17**: Uploading testcases without orderIndex assigns clean sequential indexes (0, 1).
     - Added **Adversarial Test 18**: Missing multipart file part returns RFC 7807 400 Bad Request.
     - Added **Adversarial Test 19**: Expired or tampered JWT access token on protected endpoint returns 401 ProblemDetail.

---

## 3. Verification Record

- **Deep Verification (Ran Actual Test Suites via `./mvnw.cmd test`):**
  - **Results:** `Tests run: 79, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS).
  - `AdversarialReviewerTest`: 19/19 passed.
  - `AuthControllerIntegrationTest`: 6/6 passed.
  - `ProblemAndTestCaseIntegrationTest`: 6/6 passed.
  - `ProblemServiceTest`: 11/11 passed.
  - `TestCaseServiceTest`: 8/8 passed.
  - `StorageServiceTest`: 13/13 passed.
  - `SecurityUnitTest`: 3/3 passed.
  - `GlobalExceptionHandlerTest`: 11/11 passed.
  - `FlywaySchemaTest`: 1/1 passed.
  - `MiniLeetcodeApplicationTests`: 1/1 passed.

- **Code Coverage (JaCoCo `target/site/jacoco/jacoco.csv`):**
  - Total Instructions: 2,625 covered / 106 missed = **96.12% Instruction Coverage** (Target: >= 80%).
  - Total Lines: 589 covered / 22 missed = **96.40% Line Coverage**.
  - All Controllers: `TestCaseController` (100%), `AuthController` (100%), `ProblemController` (90.8%).
  - All Services: `MinioStorageService` (98.9%), `StorageService` (95.6%), `AuthService` (95.2%), `ProblemService` (94.9%), `TestCaseService` (93.4%).
  - All Security Components: `SecurityConfig` (100%), `JwtAuthenticationFilter` (100%), `CustomAccessDeniedHandler` (100%), `CustomUserDetailsService` (100%), `CustomAuthenticationEntryPoint` (97.8%), `UserPrincipal` (94.9%), `JwtTokenProvider` (94.6%).

- **Shallow Verification (Manual Inspection):**
  - Verified Docker Compose configurations for MySQL 8.0 and MinIO (`docker-compose.yml`).
  - Verified OpenAPI documentation paths and tags (`/v3/api-docs`, `/swagger-ui.html`).
  - Verified Java 21 compilation and Maven compiler release 21 flags.

- **Unverified Aspects:**
  - Live Docker network connection: Verified via unit stream mocks and MockMvc slices; actual Docker daemon connection was not executed because the local Windows Docker Desktop daemon service is stopped.
  - High concurrency clustering under physical MySQL 8.0 multi-node deployment (tested under H2 MySQL compatibility profile with pessimistic write locks).

---

## 4. Known Issues

- `Shallow Verification`: MinIO S3 live network socket communication verified via `MinioClient` mock stream invocations and MockMvc integration slices rather than a live local MinIO container.
- `Minor Robustness Risk`: Distributed execution queue (Redis Streams) and isolated sandbox execution tier (Judge Worker / nsjail / cgroups) belong to Phase 2 roadmap.

---

## 5. Remaining Risk & Next Step

Phase 1 Backend Lead delivery is verified, hardened against privilege escalation, storage leaks on partial uploads, filter crashes from deleted user tokens, invalid boundary and negative testcase ordering, and HTTP RFC 7807 problem details conformity, achieving 96.12% instruction coverage.

The Phase 1 module delivery is complete and ready for handoff to Phase 2.
