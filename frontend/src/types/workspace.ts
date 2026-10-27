// src/types/workspace.ts

export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type ProblemStatus = 'DRAFT' | 'REVIEWING' | 'PUBLISHED' | 'ARCHIVED';
export type Language = 'JAVA' | 'PYTHON' | 'CPP';
export type SubmissionStatus = 'PENDING' | 'JUDGING' | 'COMPLETED' | 'FAILED';
export type Verdict =
  | 'AC'
  | 'WA'
  | 'TLE'
  | 'MLE'
  | 'CE'
  | 'RE'
  | 'SV'
  | 'FLAGGED_CHEATING'
  | null;

export type ProctoringEventType =
  | 'WINDOW_BLUR'
  | 'WINDOW_FOCUS'
  | 'PASTE_SPIKE'
  | 'HEARTBEAT_TIMEOUT';

export interface PublicTestCase {
  id: number;
  inputPreview: string;
  expectedOutputPreview: string;
  orderIndex: number;
}

export interface ProblemDetail {
  id: number;
  title: string;
  slug: string;
  description: string; // Nội dung Markdown: Mô tả, Constraints, Examples
  difficulty: Difficulty;
  tags: string[];
  timeLimitMs: number; // 500ms - 5000ms
  memoryLimitMb: number; // 64MB - 512MB
  status: ProblemStatus;
  templates: Record<Language, string>; // Code mẫu khởi tạo theo ngôn ngữ
  publicTestCases: PublicTestCase[];
}

// POST /api/v1/submissions Request & Response
export interface CreateSubmissionRequest {
  problemId: number;
  contestId: number | null;
  language: Language;
  code: string;
}

export interface CreateSubmissionResponse {
  code: number;
  message: string;
  data: {
    submissionId: number;
    status: SubmissionStatus;
    initialRetryDelayMs: number;
  };
}

// GET /api/v1/submissions/{submissionId} Response
export interface SubmissionDetailResponse {
  code: number;
  message: string;
  data: {
    id: number;
    status: SubmissionStatus;
    verdict: Verdict;
    executionTimeMs: number | null;
    memoryUsedKb: number | null;
    passedRatio: string; // Ví dụ: "15/15"
    errorMessage: string | null;
  };
}

// POST /api/v1/contests/{contestId}/events Request & Response
export interface ProctoringEventRequest {
  eventType: ProctoringEventType;
  clientTimestamp: number;
}

export interface ProctoringEventResponse {
  code: number;
  message: string;
  data: {
    violationRegistered: boolean;
    currentViolations: number;
    maxViolations: number;
    isDisqualified: boolean;
    message: string;
  };
}