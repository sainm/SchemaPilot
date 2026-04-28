package org.sainm.schemapilot.ai;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;

@Component
public class SensitiveValueRedactor {
    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("(?i)(password|passwd|pwd)\\s*[:=]\\s*([^\\s;,'\"]+)"), "$1=<redacted>"),
            new Rule(Pattern.compile("(?i)(api[_-]?key|access[_-]?key|secret|token)\\s*[:=]\\s*([^\\s;,'\"]+)"), "$1=<redacted>"),
            new Rule(Pattern.compile("(?i)jdbc:(oracle|postgresql):([^\\s'\"]+)"), "jdbc:$1:<redacted>")
    );

    public String redact(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        var redacted = value;
        for (var rule : RULES) {
            redacted = rule.pattern().matcher(redacted).replaceAll(rule.replacement());
        }
        return redacted;
    }

    private record Rule(Pattern pattern, String replacement) {
    }
}
