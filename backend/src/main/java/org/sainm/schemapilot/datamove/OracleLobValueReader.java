package org.sainm.schemapilot.datamove;

import java.io.IOException;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.SQLException;

public class OracleLobValueReader {
    private final MemoryBudgetManager memoryBudgetManager;
    private final int chunkBytes;

    public OracleLobValueReader(MemoryBudgetManager memoryBudgetManager) {
        this(memoryBudgetManager, (int) Math.min(Integer.MAX_VALUE, Math.max(8192, memoryBudgetManager.shardBudgetBytes())));
    }

    OracleLobValueReader(MemoryBudgetManager memoryBudgetManager, int chunkBytes) {
        this.memoryBudgetManager = memoryBudgetManager;
        this.chunkBytes = chunkBytes;
    }

    public String read(Object value) {
        try {
            if (value == null) {
                return null;
            }
            if (value instanceof Clob clob) {
                return readClob(clob);
            }
            if (value instanceof Blob blob) {
                return readBlob(blob);
            }
            if (value instanceof byte[] bytes) {
                return readBytes(bytes);
            }
            return value.toString();
        } catch (Exception ex) {
            throw new DataMoveException("Oracle LOB chunk reader failed: " + ex.getMessage(), ex);
        }
    }

    private String readClob(Clob clob) throws SQLException, IOException {
        var target = new StringBuilder();
        var chars = new char[Math.max(1024, chunkBytes / 4)];
        try (var reader = clob.getCharacterStream();
             var buffer = new FfmLobChunkBuffer(memoryBudgetManager, chunkBytes)) {
            int read;
            while ((read = reader.read(chars)) >= 0) {
                buffer.appendUtf8(chars, read, target);
            }
            buffer.flushUtf8To(target);
            return target.toString();
        }
    }

    private String readBlob(Blob blob) throws SQLException, IOException {
        var target = new StringBuilder("\\x");
        var bytes = new byte[Math.max(1024, chunkBytes)];
        try (var input = blob.getBinaryStream();
             var buffer = new FfmLobChunkBuffer(memoryBudgetManager, chunkBytes)) {
            int read;
            while ((read = input.read(bytes)) >= 0) {
                buffer.appendHex(bytes, read, target);
            }
            buffer.flushHexTo(target);
            return target.toString();
        }
    }

    private String readBytes(byte[] bytes) {
        var target = new StringBuilder("\\x");
        try (var buffer = new FfmLobChunkBuffer(memoryBudgetManager, chunkBytes)) {
            buffer.appendHex(bytes, bytes.length, target);
            buffer.flushHexTo(target);
            return target.toString();
        }
    }
}
