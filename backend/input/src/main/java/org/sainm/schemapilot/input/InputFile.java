package org.sainm.schemapilot.input;

public record InputFile(
        String relativePath,
        byte[] content) {

    public InputFile {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
