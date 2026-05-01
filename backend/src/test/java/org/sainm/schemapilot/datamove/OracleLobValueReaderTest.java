package org.sainm.schemapilot.datamove;

import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;

import static org.assertj.core.api.Assertions.assertThat;

class OracleLobValueReaderTest {
    @Test
    void readsClobThroughFfmChunksAndReleasesMemory() throws Exception {
        var budget = new MemoryBudgetManager(4096, 4096, 32);
        var reader = new OracleLobValueReader(budget, 32);
        var clob = new SerialClob("hello-migration-platform-database".toCharArray());

        var value = reader.read(clob);

        assertThat(value).isEqualTo("hello-migration-platform-database");
        assertThat(budget.reservedBytes()).isZero();
        assertThat(budget.activeLeases()).isZero();
    }

    @Test
    void readsBlobAsPostgresByteaHexThroughFfmChunks() throws Exception {
        var budget = new MemoryBudgetManager(4096, 4096, 4);
        var reader = new OracleLobValueReader(budget, 4);
        var blob = new SerialBlob(new byte[]{0x00, 0x01, 0x0f, 0x10, (byte) 0xff});

        var value = reader.read(blob);

        assertThat(value).isEqualTo("\\x00010f10ff");
        assertThat(budget.reservedBytes()).isZero();
        assertThat(budget.activeLeases()).isZero();
    }

    @Test
    void readsByteArrayAsPostgresByteaHex() {
        var budget = new MemoryBudgetManager(4096, 4096, 4);
        var reader = new OracleLobValueReader(budget, 4);

        var value = reader.read(new byte[]{0x2a, 0x2b});

        assertThat(value).isEqualTo("\\x2a2b");
        assertThat(budget.reservedBytes()).isZero();
        assertThat(budget.activeLeases()).isZero();
    }
}
