package net.minecraft.util.filefix;

public class AtomicMoveNotSupportedFileFixException extends FileFixException {
    public AtomicMoveNotSupportedFileFixException(FileSystemCapabilities fileSystemCapabilities) {
        super(null, fileSystemCapabilities);
    }
}
