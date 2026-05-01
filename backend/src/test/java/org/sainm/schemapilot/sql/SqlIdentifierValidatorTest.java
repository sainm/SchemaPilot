package org.sainm.schemapilot.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SqlIdentifierValidatorTest {
    private final SqlIdentifierValidator validator = new SqlIdentifierValidator();

    @Test
    void quotesSimpleAndQualifiedIdentifiers() {
        assertThat(validator.quote("orders")).isEqualTo("\"orders\"");
        assertThat(validator.quoteQualified("hr.orders")).isEqualTo("\"hr\".\"orders\"");
        assertThat(validator.quoteQualified("\"HR\".\"Orders\"")).isEqualTo("\"HR\".\"Orders\"");
    }

    @Test
    void rejectsInjectedIdentifierFragments() {
        assertThatThrownBy(() -> validator.quoteQualified("orders; drop table users"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsafe");
        assertThatThrownBy(() -> validator.quote("order id"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("allowlisted");
    }
}
