# Victory Auditor Setup
Working directory: c:\Users\aDMIN\Documents\JSD\Mini Leetcode\.agents\teamwork\teamwork_preview_victory_auditor_r1
Project: Mini LeetCode Platform Phase 1

## 2026-10-23T12:54:01Z
You are teamwork_preview_victory_auditor.
Your working directory is: c:\Users\aDMIN\Documents\JSD\Mini Leetcode\.agents\teamwork\teamwork_preview_victory_auditor_r1
The project codebase is at: c:\Users\aDMIN\Documents\JSD\Mini Leetcode\mini_leetcode
The project root is: c:\Users\aDMIN\Documents\JSD\Mini Leetcode

<original_task>
This is a single self-contained fix/module delivery; keep it small and focused.
Triển khai Phase 1 của dự án Mini LeetCode Platform dành riêng cho vai trò Backend Lead theo từng bước nhỏ (incremental slices), kiểm tra cẩn thận trước khi implement, tuân thủ nguyên tắc Ponytail (đơn giản, tinh gọn, YAGNI, không over-engineering): thiết lập chuẩn Spring Boot 3.3.x trên Java 21 LTS, MySQL 8.0 với Flyway Migration, Stateless JWT Authentication & Refresh Token Revocation, tích hợp MinIO SDK cho Testcase file storage, cùng hệ thống RESTful CRUD APIs chuẩn OpenAPI 3.1 và RFC 7807 Problem Details.

Working directory: c:\Users\aDMIN\Documents\JSD\Mini Leetcode\mini_leetcode
Integrity mode: development

## Implementation Slices (Tách nhỏ công việc & Kiểm thử từng bước)

### Slice 1: Base Infrastructure, Docker Compose & Flyway Migration
- Sửa file `pom.xml`: Khắc phục phiên bản lỗi parent 4.1.1 thành Spring Boot 3.3.4 (hoặc 3.3.x ổn định), Java 21 LTS. Bổ sung dependencies tối thiểu cần thiết: Spring Web, Spring Security, Spring Data JPA, MySQL Connector, Flyway Core + Flyway MySQL, Validation, Lombok, Springdoc OpenAPI 3.1, MinIO Client SDK, H2 Database (cho test profile).
- Tạo `docker-compose.yml` tại root workspace (`c:\Users\aDMIN\Documents\JSD\Mini Leetcode\docker-compose.yml`) cho MySQL 8.0 và MinIO (cấu hình port, credentials, volume).
- Viết `V1__init_schema.sql` trong `src/main/resources/db/migration/` tạo chuẩn 6 bảng: `users`, `problems`, `test_cases`, `contests`, `contest_participants`, `submissions` với đầy đủ indexes và foreign keys theo `docs/TECHNICAL_SPECIFICATION.md`.
- Tạo cấu hình `application.properties` (hoặc `.yml`) chuẩn, tách profile `local` (MySQL/MinIO) và `test` (H2 in-memory MySQL mode).
- *Verification Gate 1:* Chạy `./mvnw clean test-compile` và viết test kiểm tra Flyway schema load thành công trên H2 test profile.

### Slice 2: Stateless Security, JWT & Refresh Token Revocation
- Xây dựng User entity, Role enum (`USER`, `INSTRUCTOR`, `ADMIN`), UserRepository.
- Triển khai Security Configuration (Spring Security 6): `SecurityFilterChain`, `BCryptPasswordEncoder` (work factor 12), stateless session management.
- Cung cấp JWT Token Provider & Filter (Access Token 30m, Refresh Token 7d có lưu DB để revoke/logout).
- Xây dựng AuthController & AuthService:
  - `POST /api/v1/auth/register`
  - `POST /api/v1/auth/login`
  - `POST /api/v1/auth/refresh`
  - `POST /api/v1/auth/logout`
- Xây dựng `GlobalExceptionHandler` hỗ trợ `ProblemDetail` chuẩn RFC 7807 cho các lỗi Auth, AccessDenied, Validation.
- *Verification Gate 2:* Viết MockMvc integration tests kiểm tra toàn bộ luồng Auth, cấp token, refresh token, revoke, và phân quyền role 403 Forbidden.

### Slice 3: MinIO Storage Service cho TestCases
- Tạo `MinioConfig` và `StorageService` interface với implementation dùng MinIO Client.
- Hỗ trợ stream upload/download file testcase input/output, tự động tạo bucket `testcases` nếu chưa tồn tại.
- Đảm bảo stream dữ liệu trực tiếp, không load toàn bộ byte vào bộ nhớ Java heap.
- *Verification Gate 3:* Viết unit/mock test cho StorageService xác minh luồng stream I/O và xử lý exception.

### Slice 4: Problem & TestCase Management RESTful APIs
- Xây dựng Problem entity, TestCase entity, repositories.
- Tạo DTOs (Records) cho Request/Response và mapper sạch gọn.
- Xây dựng ProblemService & ProblemController:
  - Tạo đề (tự động sinh slug duy nhất), cập nhật đề theo vòng đời (`DRAFT` -> `REVIEWING` -> `PUBLISHED` -> `ARCHIVED`).
  - Lấy chi tiết đề theo slug hoặc id.
  - Tìm kiếm & phân trang bài tập theo difficulty, status, tag.
- Xây dựng TestCaseService & TestCaseController:
  - Upload testcase input/output lên StorageService, lưu metadata vào DB.
  - Lấy danh sách testcase: Người dùng thường chỉ nhận testcase công khai (`is_hidden = false`), Instructor/Admin nhận toàn bộ.
- Tích hợp OpenAPI annotations (@Operation, @ApiResponse).
- *Verification Gate 4:* Viết MockMvc test suites cho Problem & TestCase endpoints, xác minh phân trang, slug generation, và lọc ẩn/hiện testcase.

## Acceptance Criteria

### Build & Verification
- [ ] `./mvnw clean test` chạy thành công 100% không có lỗi biên dịch hay lỗi runtime.
- [ ] Flyway migrations chạy mượt mà, khởi tạo thành công cấu trúc bảng và indexes.
- [ ] Code coverage cho business logic và REST controllers đạt >= 80%.

### Security & Functional Criteria
- [ ] Đăng ký, đăng nhập thành công trả về JWT hợp lệ; sai thông tin trả về HTTP 401 với body RFC 7807.
- [ ] Truy cập endpoint yêu cầu quyền INSTRUCTOR/ADMIN bằng USER token trả về HTTP 403 Forbidden.
- [ ] Refresh token bị thu hồi hoặc không hợp lệ trả về HTTP 401.
- [ ] API tạo problem tự động sinh slug duy nhất, validate dữ liệu đầu vào.
- [ ] Testcase metadata lưu đúng URI, hỗ trợ phân quyền ẩn/hiện testcase rõ ràng.
</original_task>

Conduct an independent 3-phase audit:
1. Timeline & requirements audit against the 4 slices and acceptance criteria.
2. Anti-cheating & integrity detection: verify tests actually test the logic and were not mocked to pass artificially without testing requirements, check that assertions are meaningful.
3. Independent execution: run `$env:JAVA_HOME = "C:\Program Files\Java\jdk-21"; .\mvnw.cmd clean test` and verify test results and JaCoCo coverage (>= 80%).

Write your complete audit report to `c:\Users\aDMIN\Documents\JSD\Mini Leetcode\.agents\teamwork\teamwork_preview_victory_auditor_r1\audit_report.md` with a structured verdict: CONFIRMED or REJECTED. Send a message to me when done.
