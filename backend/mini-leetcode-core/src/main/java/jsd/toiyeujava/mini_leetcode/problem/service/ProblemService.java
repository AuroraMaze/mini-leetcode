package jsd.toiyeujava.mini_leetcode.problem.service;
import jsd.toiyeujava.mini_leetcode.storage.service.StorageService;

import jakarta.persistence.criteria.Predicate;
import jsd.toiyeujava.mini_leetcode.problem.dto.CreateProblemRequest;
import jsd.toiyeujava.mini_leetcode.problem.dto.ProblemResponse;
import jsd.toiyeujava.mini_leetcode.problem.dto.UpdateProblemRequest;
import jsd.toiyeujava.mini_leetcode.entity.Difficulty;
import jsd.toiyeujava.mini_leetcode.problem.entity.Problem;
import jsd.toiyeujava.mini_leetcode.entity.ProblemStatus;
import jsd.toiyeujava.mini_leetcode.testcase.entity.TestCase;
import jsd.toiyeujava.mini_leetcode.auth.entity.User;
import jsd.toiyeujava.mini_leetcode.exception.ResourceNotFoundException;
import jsd.toiyeujava.mini_leetcode.problem.repository.ProblemRepository;
import jsd.toiyeujava.mini_leetcode.testcase.repository.TestCaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ProblemService {

    private static final Pattern NON_LATIN = Pattern.compile("[^\\w-]");
    private static final Pattern WHITESPACE = Pattern.compile("[\\s]");
    private static final Pattern MULTI_HYPHEN = Pattern.compile("-+");

    private final ProblemRepository problemRepository;
    private final TestCaseRepository testCaseRepository;
    private final StorageService storageService;

    public ProblemService(ProblemRepository problemRepository) {
        this(problemRepository, null, null);
    }

    @Autowired
    public ProblemService(
            ProblemRepository problemRepository,
            TestCaseRepository testCaseRepository,
            StorageService storageService) {
        this.problemRepository = problemRepository;
        this.testCaseRepository = testCaseRepository;
        this.storageService = storageService;
    }

    @Transactional
    public ProblemResponse createProblem(CreateProblemRequest request, User creator) {
        String slug = generateUniqueSlug(request.title());

        Problem problem = Problem.builder()
                .title(request.title())
                .slug(slug)
                .description(request.description())
                .difficulty(request.difficulty())
                .timeLimitMs(request.getTimeLimitMsOrDefault())
                .memoryLimitMb(request.getMemoryLimitMbOrDefault())
                .status(ProblemStatus.DRAFT)
                .createdBy(creator)
                .build();

        problem = problemRepository.save(problem);
        return ProblemResponse.fromEntity(problem);
    }

    @Transactional
    public ProblemResponse updateProblem(Long id, UpdateProblemRequest request) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new IllegalArgumentException("Problem title cannot be blank");
            }
            if (!request.title().trim().equals(problem.getTitle())) {
                problem.setTitle(request.title().trim());
                problem.setSlug(generateUniqueSlug(request.title().trim()));
            }
        }
        if (request.description() != null) {
            if (request.description().isBlank()) {
                throw new IllegalArgumentException("Problem description cannot be blank");
            }
            problem.setDescription(request.description());
        }
        if (request.difficulty() != null) {
            problem.setDifficulty(request.difficulty());
        }
        if (request.timeLimitMs() != null) {
            problem.setTimeLimitMs(request.timeLimitMs());
        }
        if (request.memoryLimitMb() != null) {
            problem.setMemoryLimitMb(request.memoryLimitMb());
        }
        if (request.status() != null) {
            problem.setStatus(request.status());
        }

        problem = problemRepository.save(problem);
        return ProblemResponse.fromEntity(problem);
    }

    @Transactional(readOnly = true)
    public ProblemResponse getProblemById(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));
        return ProblemResponse.fromEntity(problem);
    }

    @Transactional(readOnly = true)
    public ProblemResponse getProblemBySlug(String slug) {
        Problem problem = problemRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with slug: " + slug));
        return ProblemResponse.fromEntity(problem);
    }

    @Transactional(readOnly = true)
    public Page<ProblemResponse> getProblems(Difficulty difficulty, ProblemStatus status, String search, Pageable pageable) {
        Specification<Problem> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (difficulty != null) {
                predicates.add(cb.equal(root.get("difficulty"), difficulty));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(search)) {
                String searchPattern = "%" + search.toLowerCase(Locale.ROOT).trim() + "%";
                Predicate titleMatch = cb.like(cb.lower(root.get("title")), searchPattern);
                Predicate descMatch = cb.like(cb.lower(root.get("description")), searchPattern);
                predicates.add(cb.or(titleMatch, descMatch));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return problemRepository.findAll(spec, pageable).map(ProblemResponse::fromEntity);
    }

    @Transactional
    public void deleteProblem(Long id) {
        Problem problem = problemRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Problem not found with id: " + id));

        if (testCaseRepository != null && storageService != null) {
            List<TestCase> testCases = testCaseRepository.findByProblemIdOrderByOrderIndexAsc(id);
            for (TestCase tc : testCases) {
                storageService.deleteFileByUri(tc.getInputFileUri());
                storageService.deleteFileByUri(tc.getExpectedOutputUri());
            }
            testCaseRepository.deleteAll(testCases);
        }

        problemRepository.delete(problem);
    }

    public String generateUniqueSlug(String title) {
        String baseSlug = toSlug(title);
        if (!problemRepository.existsBySlug(baseSlug)) {
            return baseSlug;
        }

        int count = 1;
        while (true) {
            String candidate = baseSlug + "-" + count;
            if (!problemRepository.existsBySlug(candidate)) {
                return candidate;
            }
            count++;
        }
    }

    public static String toSlug(String input) {
        if (!StringUtils.hasText(input)) {
            return "problem";
        }
        String replaced = input.trim().replace('đ', 'd').replace('Đ', 'D');
        String nowhitespace = WHITESPACE.matcher(replaced).replaceAll("-");
        String normalized = Normalizer.normalize(nowhitespace, Normalizer.Form.NFD);
        String slug = NON_LATIN.matcher(normalized).replaceAll("");
        slug = MULTI_HYPHEN.matcher(slug).replaceAll("-").toLowerCase(Locale.ENGLISH);
        if (slug.startsWith("-")) {
            slug = slug.substring(1);
        }
        if (slug.endsWith("-")) {
            slug = slug.substring(0, slug.length() - 1);
        }
        return slug.isEmpty() ? "problem" : slug;
    }
}
