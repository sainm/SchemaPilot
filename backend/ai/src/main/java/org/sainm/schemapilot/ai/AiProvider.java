package org.sainm.schemapilot.ai;

public interface AiProvider {

    String name();

    AiSuggestion suggest(AiSuggestionType type, AiContext context);
}
