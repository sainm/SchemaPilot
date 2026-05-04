package org.sainm.schemapilot.convert;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OracleTypeMapperTest {

    private final OracleTypeMapper mapper = new OracleTypeMapper();

    @Test
    void mapsNumberIntegerLikeTypesToNumericByDefault() {
        assertThat(mapper.map("NUMBER(10,0)").targetType()).isEqualTo("numeric(10,0)");
        assertThat(mapper.map("NUMBER(19,0)").targetType()).isEqualTo("numeric(19,0)");
        assertThat(mapper.map("NUMBER(8,0)").targetType()).isEqualTo("numeric(8,0)");
    }

    @Test
    void mapsCommonTextAndLobTypes() {
        assertThat(mapper.map("VARCHAR2(120)").targetType()).isEqualTo("varchar(120)");
        assertThat(mapper.map("NVARCHAR2(40)").targetType()).isEqualTo("varchar(40)");
        assertThat(mapper.map("CLOB").targetType()).isEqualTo("text");
        assertThat(mapper.map("BLOB").targetType()).isEqualTo("bytea");
    }

    @Test
    void mapsOracleCharLengthSemanticsToValidVarchar() {
        TypeMappingResult mapping = mapper.map("VARCHAR2(120 CHAR)");

        assertThat(mapping.targetType()).isEqualTo("varchar(120)");
        assertThat(mapping.reviewRequired()).isTrue();
        assertThat(mapping.notes()).anySatisfy(note -> assertThat(note).contains("length semantics"));
    }

    @Test
    void marksDateAndUnboundedNumberForReview() {
        assertThat(mapper.map("DATE").reviewRequired()).isTrue();
        assertThat(mapper.map("NUMBER").reviewRequired()).isTrue();
    }
}
