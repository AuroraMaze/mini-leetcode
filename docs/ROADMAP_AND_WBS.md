# KẾ HOẠCH TRIỂN KHAI & MA TRẬN PHÂN CHIA NHIỆM VỤ (WBS)
## DỰ ÁN: MINI LEETCODE PLATFORM
**Quy mô đội ngũ:** 3 Kỹ sư (1 Backend Lead, 1 Sandbox/Judge Specialist, 1 Frontend Lead)  
**Thời gian:** 6 Tuần (3 Phases)

---

## 1. PHÂN CHIA VAI TRÒ & NGUYÊN TẮC HỢP TÁC (TEAM RACI MATRIX)

Nhằm tối đa hóa tính độc lập khi phát triển và hạn chế xung đột, 3 kỹ sư làm việc dựa trên **API Contract và Test Interface đã được thống nhất**:

| Trách nhiệm | Kỹ sư 1 (Backend Lead) | Kỹ sư 2 (Sandbox Specialist) | Kỹ sư 3 (Frontend Lead) |
|---|:---:|:---:|:---:|
| **Quản lý CSDL MySQL & Flyway Migration** | **A / R** (Chịu trách nhiệm chính) | C (Tham vấn cấu trúc Testcase) | I (Nhận thông tin) |
| **Bảo mật JWT, Quyền Hạn & APIs Nghiệp vụ** | **A / R** | I | C (Phối hợp Contract) |
| **Hàng đợi Redis Streams & Consumer Groups** | C (Phần Publisher `XADD`) | **A / R** (Phần Consumer `XREADGROUP`) | I |
| **Xây dựng Runner Sandbox, Linux Seccomp & Cgroups** | I | **A / R** | I |
| **Thu thập Metrics (Runtime ms, Peak RAM, Verdicts)** | I | **A / R** | I |
| **Next.js 14, Monaco Editor & UI/UX** | I | I | **A / R** |
| **Client Proctoring (Blur/Focus, Auto-save Draft)** | C (Xác thực phía Server) | I | **A / R** (Bắt sự kiện phía Browser) |
| **Docker Compose Môi trường chạy thử & Đóng gói** | C | **A / R** | C |

*(Ghi chú: **R** - Responsible: Người trực tiếp làm; **A** - Accountable: Người chịu trách nhiệm bàn giao; **C** - Consulted: Người được hỏi ý kiến; **I** - Informed: Người nhận thông tin)*

---

## 2. KẾ HOẠCH LỘ TRÌNH 3 GIAI ĐOẠN (ROADMAP THEO TUẦN)

```mermaid
gantt
    title LỘ TRÌNH 6 TUẦN TRIỂN KHAI MINI LEETCODE (3 PHASES)
    dateFormat  YYYY-MM-DD
    section Phase 1: Nền tảng Cốt lõi & Runner Độc lập (Tuần 1 - 2)
    Khởi tạo Git Repo & Cấu hình Docker Compose Cục bộ :p1_1, 2026-10-06, 3d
    Backend: DDL MySQL 8.0, Flyway & Auth JWT           :p1_2, after p1_1, 4d
    Sandbox: Xây dựng Runner độc lập (Cgroups, Seccomp) :p1_3, 2026-10-08, 6d
    Frontend: Giao diện Workspace, Monaco Editor        :p1_4, 2026-10-09, 5d
    Integrate: Chạy thử bài nộp đầu tiên (End-to-End)   :p1_5, after p1_3, 3d
    section Phase 2: Hàng đợi Bền vững & Phòng thi Giám sát (Tuần 3 - 4)
    Hàng đợi Redis Streams (XADD / XREADGROUP / XCLAIM) :p2_1, 2026-10-20, 5d
    Quản lý Testcase File Storage (Streaming I/O)       :p2_2, 2026-10-22, 4d
    Frontend: Proctoring Events Tracker & Auto-save     :p2_3, 2026-10-23, 5d
    Server-side Proctoring Evaluator & Lockout Flow     :p2_4, after p2_3, 4d
    section Phase 3: Leaderboard, Tối ưu & Bàn giao (Tuần 5 - 6)
    Bảng xếp hạng Contest thời gian thực (ACM/ICPC)    :p3_1, 2026-11-03, 5d
    Lịch sử bài nộp, Chi tiết Diff Testcase & Phân tích :p3_2, 2026-11-05, 5d
    Kiểm thử Tải (Load Test), Chống Thundering Herd     :p3_3, 2026-11-08, 4d
    Nghiệm thu toàn bộ Acceptance Criteria & Bàn giao   :p3_4, after p3_2, 4d
```

---

## 3. PHÂN BỔ CHI TIẾT CÔNG VIỆC CHO TỪNG KỸ SƯ

### Kỹ sư 1: Backend Core & Database Lead
- **Giai đoạn 1 (Tuần 1 - 2):**
  - Thiết lập Spring Boot 3.x project, cấu hình Flyway migration cho schema chuẩn (`users`, `problems`, `test_cases`, `contests`, `submissions`).
  - Xây dựng hệ thống xác thực Stateless JWT (Đăng ký, Đăng nhập, Phân quyền `USER`, `INSTRUCTOR`, `ADMIN`).
  - Viết CRUD APIs cho Đề bài (`problems`) và Quản lý Testcase metadata.
- **Giai đoạn 2 (Tuần 3 - 4):**
  - Triển khai Redis Streams Publisher (`XADD queue.submissions`) khi nhận bài nộp.
  - Xây dựng dịch vụ lưu trữ file testcase (File Storage Service), kiểm tra giới hạn kích thước file.
  - Triển khai API `/api/v1/contests/{id}/events` tiếp nhận sự kiện mất focus, tính toán $\Delta T$ và quản lý bộ đếm vi phạm Server-side.
- **Giai đoạn 3 (Tuần 5 - 6):**
  - Tính điểm Contest theo luật ACM/ICPC (tính điểm thưởng và thời gian phạt penalty).
  - Tích hợp Redis Caching cho bài tập hot và áp dụng Rate Limiting bảo vệ API.
  - Viết Unit Tests & Integration Tests đạt coverage > 80%.

### Kỹ sư 2: Sandbox & Judge Engine Specialist
- **Giai đoạn 1 (Tuần 1 - 2):**
  - Tạo base Docker images cho Java, Python, C++ (Alpine/Debian-slim, non-root user `1001:1001`).
  - Thiết lập Linux Seccomp profile chặn system calls nguy hiểm (`socket`, `ptrace`, `setuid`).
  - Viết runner script đo lường CPU Execution Time (ms) và Peak Memory (cgroups) độc lập với container startup overhead.
- **Giai đoạn 2 (Tuần 3 - 4):**
  - Xây dựng dịch vụ Worker độc lập (chạy consumer group `judge-group` qua `XREADGROUP`).
  - Triển khai cơ chế phục hồi "Zombie Jobs" bằng `XPENDING` và `XCLAIM` để tự động xử lý lại các bài nộp bị gián đoạn khi worker gặp sự cố.
  - Tối ưu I/O testcase: Truyền dữ liệu dạng File Stream, không load toàn bộ vào RAM của Java.
  - Bắt chuẩn xác 7 mã Verdict: `AC`, `WA`, `TLE`, `MLE`, `CE`, `RE`, `SV`.
- **Giai đoạn 3 (Tuần 5 - 6):**
  - Xây dựng module đối soát đạo văn mã nguồn cơ bản (Token-based Jaccard similarity) quét qua các bài nộp cùng một đề.
  - Stress-test môi trường Sandbox: Thử nghiệm mã độc, Fork bomb, Memory exhaustion để kiểm tra tính vững chắc của cgroups.

### Kỹ sư 3: Frontend Lead & Workspace Specialist
- **Giai đoạn 1 (Tuần 1 - 2):**
  - Khởi tạo Next.js 14 App Router, TypeScript, Tailwind CSS, Shadcn UI.
  - Xây dựng giao diện Danh sách bài tập (Lọc theo độ khó, tags, tìm kiếm).
  - Thiết kế màn hình làm bài (Split screen: Đề bài Markdown bên trái, Monaco Editor bên phải).
  - Tích hợp Monaco Editor: Đổi ngôn ngữ nạp code template mẫu, syntax highlight, phím tắt.
- **Giai đoạn 2 (Tuần 3 - 4):**
  - Xây dựng Module Giám sát Phòng thi: Bắt sự kiện `visibilitychange`, `blur`/`focus` gửi tín hiệu về server.
  - Thiết kế Modal cảnh báo vi phạm đè màn hình khi thí sinh quay lại sau khi rời tab.
  - Triển khai cơ chế Auto-save Draft cục bộ (LocalStorage) và đồng bộ Server mỗi 15s để chống rớt mạng.
  - Tích hợp cơ chế Adaptive Polling (kèm Exponential Backoff & Jitter) để nhận kết quả chấm bài từ Server.
- **Giai đoạn 3 (Tuần 5 - 6):**
  - Xây dựng trang Bảng xếp hạng Contest (Leaderboard) hiển thị thứ hạng, số bài AC, tổng điểm phạt thời gian.
  - Xây dựng trang Lịch sử làm bài (Submission Details) với giao diện xem lại code cũ và Diff kết quả testcase công khai.
  - Hoàn thiện UI phản hồi trực quan khi gặp lỗi kết nối hoặc bị khóa bài do gian lận.
