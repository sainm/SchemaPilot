package org.sainm.schemapilot.datamove;

public class MemoryBudgetExceededException extends RuntimeException {
    public MemoryBudgetExceededException(String message) {
        super(message);
    }
}
