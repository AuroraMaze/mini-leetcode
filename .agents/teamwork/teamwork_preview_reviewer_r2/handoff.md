# Mini LeetCode Platform - Phase 1 Adversarial Reviewer Handoff Report (Round 2)

> [!WARNING] **Skepticism Disclaimer**
> While all 68 tests now pass with 94.8% instruction coverage and privilege escalation, orphaned storage leaks, and unhandled client exceptions were eliminated, MinIO and MySQL socket behavior against live Docker containers remains unverified in this test cycle due to local Docker daemon unavailability.

---

## 1. What the Prior Attempt Got Wrong

### Issue 1: Privilege Escalation via Public Registration Mass Assignment
- **Input:** `POST /api/v1/auth/register` with request payload `{"username": "attacker", "email": "attacker@example.com", "password": "Password123!", "role": "ADMIN"}` (or `role: "INSTRUCTOR"`).
- **Expected:** The server must strictly ignore or reject privileged roles on public self-registration endpoints, assigning `Role.USER` only.
- **Actual:** `AuthService.register` evaluated `Role assignedRole = request.role() != null ? request.role() : Role.USER;` and created an `ADMIN` account directly in the database. Furthermore, previous integration tests used this very vulnerability to seed instructor/admin accounts in test suites.
- **Root Cause:** Lack of role assignment protection at trust boundary in `AuthService.register`.

### Issue 2: Orphaned MinIO Storage Objects upon Problem Deletion
- **Input:** Calling `DELETE /api/v1/problems/{id}` or `problemService.deleteProblem(id)` on a problem that contains associated testcases.
- **Expected:** Both database records (cascaded or deleted) and all corresponding physical files stored in MinIO (`inputFileUri`, `expectedOutputUri`) must be removed.
- **Actual:** The database rows were removed via MySQL cascade, but all MinIO storage objects remained orphaned in the S3 bucket indefinitely.
- **Root Cause:** `ProblemService.deleteProblem` only invoked `problemRepository.deleteById(id)` without querying testcase URIs and calling storage deletion before removing problem entities.

### Issue 3: Unhandled Spring MVC Client Exceptions Returning HTTP 500 ProblemDetail
- **Input:** Client sends:
  1. Invalid sorting column: `GET /api/v1/problems?sort=nonExistentField,desc`
  2. Malformed JSON payload: `POST /api/v1/auth/login` with `{ unclosed-json: `
  3. Blank title update: `PUT /api/v1/problems/{id}` with `{"title": "   "}`
- **Expected:** Standard RFC 7807 Problem Details response with HTTP 400 Bad Request.
- **Actual:** Fell through to `handleGenericException` returning HTTP 500 Internal Server Error.
- **Root Cause:** `GlobalExceptionHandler` lacked handlers for `PropertyReferenceException` and `HttpMessageNotReadableException`, and `ProblemService.updateProblem` silently bypassed blank string checks without raising validation errors.

### Issue 4: Unbounded Pagination Size and Negative Page Index
- **Input:** `GET /api/v1/problems?size=500` or `GET /api/v1/problems?page=-1`.
- **Expected:** `size` capped at maximum 100 to prevent database query memory exhaustion, and negative `page` rejected with HTTP 400 Bad Request.
- **Actual:** Negative page was silently coerced without bad request feedback, and page sizes up to 500+ were queried directly against the database without clamping.
- **Root Cause:** `ProblemController.getProblems` lacked request validation for negative pagination parameters and did not clamp page size before delegating to `problemService`.

---

## 2. What I Changed

1. **`AuthService.java`**:
   - Enforced `Role.USER` unconditionally for all public self-registrations (`User.builder()...role(Role.USER)...build()`), eliminating privilege escalation.
2. **`RegisterRequest.java`**:
   - Added convenient 3-argument constructor `RegisterRequest(username, email, password)` while retaining optional 4th argument for backwards deserialization compatibility without permitting escalation.
3. **`StorageService.java`**:
   - Added default method `deleteFileByUri(String uri)` to safely parse `minio://bucket/objectName` URIs and delete underlying files without duplicating parsing code across services.
4. **`TestCaseService.java`**:
   - Refactored `deleteTestCase` to use `storageService.deleteFileByUri(...)`, removing duplicated URI extraction helpers.
5. **`ProblemService.java`**:
   - Added dependencies `TestCaseRepository` and `StorageService` with `@Autowired` multi-arg constructor and backward-compatible single-arg constructor.
   - Updated `deleteProblem(Long id)` to retrieve all associated testcases and call `storageService.deleteFileByUri(...)` for all input and output files prior to database deletion.
   - Added validation in `updateProblem`: throws `IllegalArgumentException("Problem title cannot be blank")` and `IllegalArgumentException("Problem description cannot be blank")` when non-null blank strings are passed.
6. **`ProblemController.java`**:
   - Added validation in `getProblems`: validates `page >= 0` and `size >= 1`, throwing `IllegalArgumentException` on invalid values (returning HTTP 400 ProblemDetail).
   - Enforced hard ceiling on pageable size: `Math.min(pageable.getPageSize(), 100)` to prevent unbounded query allocations.
7. **`GlobalExceptionHandler.java`**:
   - Added exception handlers for:
     - `PropertyReferenceException.class` (HTTP 400 Bad Request - Invalid sort property)
     - `HttpMessageNotReadableException.class` (HTTP 400 Bad Request - Malformed JSON body)
     - `HttpRequestMethodNotSupportedException.class` (HTTP 405 Method Not Allowed)
     - `MissingServletRequestParameterException.class` (HTTP 400 Bad Request)
8. **`application.yml`**:
   - Configured `spring.data.web.pageable.max-page-size: 100`.
9. **Test Suites**:
   - **`ProblemAndTestCaseIntegrationTest.java`**: Fixed test setup to properly provision Instructor and Admin accounts in the database via `userRepository` and authenticate via `/api/v1/auth/login`.
   - **`ProblemServiceTest.java`**: Updated `setUp` with mocks for `testCaseRepository` and `storageService`, updated `deleteProblem_behaviour` to verify `deleteFileByUri`, and added `updateProblem_blankTitle_throwsException`.
   - **`GlobalExceptionHandlerTest.java`**: Added tests for `handleMethodNotSupported` (405) and `HttpMessageNotReadableException` (400).
   - **`StorageServiceTest.java`**: Added unit tests for `deleteFileByUri` verifying object parsing and `minioClient.removeObject` call.
   - **`AdversarialReviewerTest.java`**: Added 7 new adversarial test cases (Tests 8 to 14):
     - Test 8: Public registration with `role=ADMIN` is forced to `USER` and denied instructor privileges.
     - Test 9: `deleteProblem` physically deletes all associated MinIO testcase files.
     - Test 10: Invalid sort property returns RFC 7807 400 Bad Request.
     - Test 11: Malformed JSON body returns RFC 7807 400 Bad Request.
     - Test 12: Updating problem with blank title returns RFC 7807 400 Bad Request.
     - Test 13: Negative page returns 400 and excessive page size is clamped to max 100.
     - Test 14: SQL Injection payloads in search query are safely parameterized.

---

## 3. Verification Record

- **Deep Verification (Ran Actual Test Suites via `./mvnw.cmd test`):**
  - **Results:** `Tests run: 68, Failures: 0, Errors: 0, Skipped: 0` (BUILD SUCCESS).
  - `AdversarialReviewerTest`: 14/14 passed.
  - `AuthControllerIntegrationTest`: 6/6 passed.
  - `ProblemAndTestCaseIntegrationTest`: 6/6 passed.
  - `ProblemServiceTest`: 11/11 passed.
  - `TestCaseServiceTest`: 5/5 passed.
  - `StorageServiceTest`: 13/13 passed.
  - `SecurityUnitTest`: 3/3 passed.
  - `GlobalExceptionHandlerTest`: 8/8 passed.
  - `FlywaySchemaTest`: 1/1 passed.
  - `MiniLeetcodeApplicationTests`: 1/1 passed.

- **Code Coverage (JaCoCo `target/site/jacoco/jacoco.csv`):**
  - Total Instructions: 2,524 covered / 139 missed = **94.78% Instruction Coverage**.
  - Total Lines: 543 covered / 33 missed = **94.27% Line Coverage**.
  - All Controllers: `AuthController` (100%), `TestCaseController` (100%), `ProblemController` (90.8%).
  - All Services: `AuthService` (95.2%), `ProblemService` (94.8%), `TestCaseService` (93.7%), `MinioStorageService` (98.9%), `StorageService` (95.5%).
  - All Security Components: `SecurityConfig` (100%), `JwtAuthenticationFilter` (100%), `UserPrincipal` (94.9%), `JwtTokenProvider` (94.4%).

- **Shallow Verification (Manual Inspection):**
  - Verified Docker Compose configurations for MySQL 8.0 and MinIO (`docker-compose.yml`).
  - Verified OpenAPI documentation paths and tags (`/v3/api-docs`, `/swagger-ui.html`).

- **Unverified Aspects:**
  - Live Docker network connection: Verified via unit stream mocks and MockMvc slices; actual Docker daemon connection was not executed because the local Windows Docker Desktop daemon service is stopped.
  - High concurrency clustering under physical MySQL 8.0 multi-node deployment (tested under H2 MySQL compatibility profile with pessimistic write locks).

---

## 4. Known Issues

- `Shallow Verification`: MinIO S3 network communication verified via `MinioClient` mock stream invocations and MockMvc slices, not a live MinIO Docker daemon.
- `Minor Robustness Risk`: Asynchronous distributed event processing (Redis Streams submission queue) and remote sandbox process jail (Docker/nsjail cgroups) are not part of Phase 1 scope and are deferred to Phase 2.

---

## 5. Remaining Risk & Next Step

Phase 1 Backend Lead delivery is verified, hardened against privilege escalation, storage leakage, concurrency races, and invalid query boundary conditions, and exceeds the >= 80% coverage requirement at 94.8%.

**Next Steps for Phase 2:**
1. Implement Redis Streams submission queuing (`queue.submissions`) with Consumer Group ACK/PEL retry workers.
2. Build isolated Sandbox Execution Tier (`judge-worker`) with Docker rootless / nsjail sandboxing, seccomp filters, and cgroup resource limits.
3. Implement Server-Side Proctoring State Machine and Heartbeat validation.
