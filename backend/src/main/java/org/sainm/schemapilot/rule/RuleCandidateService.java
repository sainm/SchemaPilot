package org.sainm.schemapilot.rule;

import org.sainm.schemapilot.common.api.BadRequestException;
import org.sainm.schemapilot.common.api.NotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Service
public class RuleCandidateService {
    private final ConcurrentHashMap<UUID, RuleCandidate> candidates = new ConcurrentHashMap<>();

    public RuleCandidate create(RuleCandidateCreateRequest request) {
        var now = Instant.now();
        var inferred = inferRule(request.originalSql(), request.revisedSql());
        var candidate = new RuleCandidate(
                UUID.randomUUID(),
                request.source(),
                blankToDefault(request.sourceProject(), "local"),
                null,
                blankToDefault(request.objectType(), "UNKNOWN"),
                blankToDefault(request.riskType(), inferred.riskType()),
                inferred.pattern(),
                inferred.replacement(),
                RuleCandidateStatus.DRAFT,
                List.of(new RuleFixture("candidate-origin", request.originalSql(), request.revisedSql(), true)),
                now,
                now
        );
        candidates.put(candidate.id(), candidate);
        return candidate;
    }

    public RuleCandidate get(UUID candidateId) {
        var candidate = candidates.get(candidateId);
        if (candidate == null) {
            throw new NotFoundException("Rule candidate not found: " + candidateId);
        }
        return candidate;
    }

    public List<RuleCandidate> list() {
        return candidates.values().stream().sorted(java.util.Comparator.comparing(RuleCandidate::createdAt)).toList();
    }

    public RuleCandidate review(UUID candidateId, RuleCandidateReviewRequest request) {
        var candidate = get(candidateId);
        var status = request.approved() ? RuleCandidateStatus.APPROVED : RuleCandidateStatus.REJECTED;
        return save(with(candidate, status, request.reviewer(), candidate.fixtures()));
    }

    public RuleCandidate addFixture(UUID candidateId, RuleFixtureRequest request) {
        var candidate = get(candidateId);
        if (candidate.status() == RuleCandidateStatus.ENABLED) {
            throw new BadRequestException("Enabled rules cannot add fixtures without creating a new candidate version.");
        }
        var fixtures = new ArrayList<>(candidate.fixtures());
        fixtures.add(new RuleFixture(request.name(), request.inputSql(), request.expectedSql(), request.shouldMatch()));
        return save(with(candidate, candidate.status(), candidate.reviewer(), List.copyOf(fixtures)));
    }

    public RuleEnableResult enable(UUID candidateId) {
        var candidate = get(candidateId);
        if (candidate.status() != RuleCandidateStatus.APPROVED) {
            throw new BadRequestException("Rule candidate must be manually approved before tests and enablement.");
        }
        var results = runFixtures(candidate);
        var passed = results.stream().allMatch(RuleTestResult::passed);
        var status = passed ? RuleCandidateStatus.ENABLED : RuleCandidateStatus.TEST_FAILED;
        return new RuleEnableResult(save(with(candidate, status, candidate.reviewer(), candidate.fixtures())), results);
    }

    public List<RuleTestResult> runFixtures(UUID candidateId) {
        return runFixtures(get(candidateId));
    }

    public RuleReapplyResponse reapply(RuleReapplyRequest request) {
        var revised = request.sql();
        var applied = new ArrayList<String>();
        for (var candidate : candidates.values()) {
            if (candidate.status() == RuleCandidateStatus.ENABLED && matchesScope(candidate, request)) {
                var updated = apply(candidate, revised);
                if (!updated.equals(revised)) {
                    revised = updated;
                    applied.add(candidate.id().toString());
                }
            }
        }
        return new RuleReapplyResponse(request.sql(), revised, List.copyOf(applied));
    }

    private List<RuleTestResult> runFixtures(RuleCandidate candidate) {
        return candidate.fixtures().stream()
                .map(fixture -> runFixture(candidate, fixture))
                .toList();
    }

    private RuleTestResult runFixture(RuleCandidate candidate, RuleFixture fixture) {
        var actual = apply(candidate, fixture.inputSql());
        var matched = !actual.equals(fixture.inputSql());
        var passed = fixture.shouldMatch()
                ? matched && normalize(actual).equals(normalize(fixture.expectedSql()))
                : !matched;
        return new RuleTestResult(fixture.name(), passed, actual, fixture.expectedSql());
    }

    private String apply(RuleCandidate candidate, String sql) {
        return Pattern.compile(candidate.pattern(), Pattern.CASE_INSENSITIVE | Pattern.DOTALL)
                .matcher(sql)
                .replaceAll(candidate.replacement());
    }

    private boolean matchesScope(RuleCandidate candidate, RuleReapplyRequest request) {
        var objectMatches = request.objectType() == null || request.objectType().isBlank() || candidate.objectType().equalsIgnoreCase(request.objectType());
        var riskMatches = request.riskType() == null || request.riskType().isBlank() || candidate.riskType().equalsIgnoreCase(request.riskType());
        return objectMatches && riskMatches;
    }

    private RuleCandidate save(RuleCandidate candidate) {
        candidates.put(candidate.id(), candidate);
        return candidate;
    }

    private RuleCandidate with(RuleCandidate candidate, RuleCandidateStatus status, String reviewer, List<RuleFixture> fixtures) {
        return new RuleCandidate(
                candidate.id(),
                candidate.source(),
                candidate.sourceProject(),
                reviewer,
                candidate.objectType(),
                candidate.riskType(),
                candidate.pattern(),
                candidate.replacement(),
                status,
                fixtures,
                candidate.createdAt(),
                Instant.now()
        );
    }

    private InferredRule inferRule(String originalSql, String revisedSql) {
        if (contains(originalSql, "\\bNVL\\s*\\(") && contains(revisedSql, "\\bCOALESCE\\s*\\(")) {
            return new InferredRule("NVL", "\\bNVL\\s*\\(", "COALESCE(");
        }
        if (contains(originalSql, "\\bSYSDATE\\b") && contains(revisedSql, "\\bCURRENT_TIMESTAMP\\b")) {
            return new InferredRule("CURRENT_TIME", "\\bSYSDATE\\b", "CURRENT_TIMESTAMP");
        }
        if (contains(originalSql, "\\bVARCHAR2\\b") && contains(revisedSql, "\\bvarchar\\b|\\btext\\b")) {
            return new InferredRule("TYPE_MAPPING", "\\bVARCHAR2\\b", "varchar");
        }
        if (contains(originalSql, "\\bNUMBER\\b") && contains(revisedSql, "\\bnumeric\\b|\\bbigint\\b|\\binteger\\b")) {
            return new InferredRule("NUMBER_PRECISION", "\\bNUMBER\\b", "numeric");
        }
        return new InferredRule("MANUAL_PATTERN", Pattern.quote(originalSql), java.util.regex.Matcher.quoteReplacement(revisedSql));
    }

    private boolean contains(String value, String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(value).find();
    }

    private String normalize(String value) {
        return value.strip().replaceAll("\\s+", " ");
    }

    private String blankToDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record InferredRule(String riskType, String pattern, String replacement) {
    }
}
