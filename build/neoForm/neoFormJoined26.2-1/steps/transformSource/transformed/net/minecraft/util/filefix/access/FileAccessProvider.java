package net.minecraft.util.filefix.access;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class FileAccessProvider implements AutoCloseable {
    private final List<FileAccess<?>> accessedFiles = new ArrayList<>();
    private final ScopedValue<Path> baseDirectory = ScopedValue.newInstance();
    private final int dataVersion;
    private boolean frozen = false;

    public FileAccessProvider(int dataVersion) {
        this.dataVersion = dataVersion;
    }

    public <T extends AutoCloseable> FileAccess<T> getFileAccess(FileResourceType<T> type, FileRelation fileRelation) {
        if (this.frozen) {
            throw new IllegalStateException("Cannot request new file access here.");
        }

        FileAccess<T> fileAccess = new FileAccess<>(this, type, fileRelation);
        this.accessedFiles.add(fileAccess);
        return fileAccess;
    }

    public void freeze() {
        this.frozen = true;
    }

    public ScopedValue<Path> baseDirectory() {
        return this.baseDirectory;
    }

    public int dataVersion() {
        return this.dataVersion;
    }

    @Override
    public void close() {
        for (FileAccess<?> accessedFile : this.accessedFiles) {
            accessedFile.close();
        }
    }
}
