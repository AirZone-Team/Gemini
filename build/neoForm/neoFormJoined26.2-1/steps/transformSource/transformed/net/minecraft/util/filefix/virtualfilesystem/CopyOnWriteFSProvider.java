package net.minecraft.util.filefix.virtualfilesystem;

import java.io.IOException;
import java.net.URI;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.AccessMode;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.DirectoryStream;
import java.nio.file.FileStore;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.FileAttributeView;
import java.nio.file.attribute.FileTime;
import java.nio.file.spi.FileSystemProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.util.DummyFileAttributes;
import net.minecraft.util.filefix.virtualfilesystem.exception.CowFSDirectoryNotEmptyException;
import net.minecraft.util.filefix.virtualfilesystem.exception.CowFSFileAlreadyExistsException;
import net.minecraft.util.filefix.virtualfilesystem.exception.CowFSFileSystemException;
import net.minecraft.util.filefix.virtualfilesystem.exception.CowFSNoSuchFileException;
import org.jspecify.annotations.Nullable;

public class CopyOnWriteFSProvider extends FileSystemProvider {
    public static final String SCHEME = "x-mc-copy-on-write";
    private static final BasicFileAttributeView DUMMY_DIRECTORY_VIEW = new BasicFileAttributeView() {
        @Override
        public String name() {
            return "basic";
        }

        @Override
        public BasicFileAttributes readAttributes() {
            return DummyFileAttributes.DIRECTORY;
        }

        @Override
        public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
        }
    };
    private final CopyOnWriteFileSystem fs;

    public CopyOnWriteFSProvider(CopyOnWriteFileSystem fileSystem) {
        this.fs = fileSystem;
    }

    @Override
    public String getScheme() {
        return "x-mc-copy-on-write";
    }

    @Override
    public FileSystem newFileSystem(URI uri, Map<String, ?> env) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileSystem getFileSystem(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Path getPath(URI uri) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SeekableByteChannel newByteChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return this.newChannel(path, options, attrs, Files::newByteChannel);
    }

    @Override
    public FileChannel newFileChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException {
        return this.newChannel(path, options, attrs, FileChannel::open);
    }

    private synchronized <C> C newChannel(
        Path path, Set<? extends OpenOption> options, FileAttribute<?>[] attrs, CopyOnWriteFSProvider.ChannelFactory<C> channelFactory
    ) throws IOException {
        CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(path);
        if (options.contains(StandardOpenOption.DELETE_ON_CLOSE)) {
            throw new UnsupportedOperationException("DELETE_ON_CLOSE is not supported by CowFS");
        }

        return (C)(switch (this.fs.fileTree().byPathOrNull(cowPath)) {
            case null -> {
                if (!options.contains(StandardOpenOption.CREATE) && !options.contains(StandardOpenOption.CREATE_NEW)) {
                    throw new CowFSNoSuchFileException(cowPath.toString());
                }

                DirectoryNode directoryNode = this.fs.fileTree().directoryByPath(Objects.requireNonNull(cowPath.getParent()));
                Path tempFile = this.fs.createTemporaryFilePath();
                C result = channelFactory.newChannel(tempFile, options, attrs);
                FileNode child = new FileNode(cowPath, tempFile, true);
                directoryNode.addChild(child);
                yield result;
            }
            case FileNode fileNode -> {
                if (wantsWrite(options)) {
                    fileNode.ensureCopy();
                }

                yield channelFactory.newChannel(fileNode.storagePath(), options, attrs);
            }
            case DirectoryNode var14 -> throw new CowFSFileSystemException(cowPath + ": not a regular file");
            default -> throw new MatchException(null, null);
        });
    }

    private static boolean wantsWrite(Set<? extends OpenOption> options) {
        return options.contains(StandardOpenOption.WRITE) || !options.contains(StandardOpenOption.READ) && options.contains(StandardOpenOption.APPEND);
    }

    @Override
    public synchronized DirectoryStream<Path> newDirectoryStream(Path dir, Filter<? super Path> filter) throws IOException {
        CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(dir);
        DirectoryNode directoryNode = this.fs.fileTree().directoryByPath(cowPath);
        final List<Path> result = new ArrayList<>();

        for (Node childNode : directoryNode.children()) {
            Path path = childNode.path();
            if (filter.accept(path)) {
                result.add(path);
            }
        }

        return new DirectoryStream<Path>() {
            @Override
            public void close() {
            }

            @Override
            public Iterator<Path> iterator() {
                return result.iterator();
            }
        };
    }

    @Override
    public synchronized void createDirectory(Path dir, FileAttribute<?>... attrs) throws IOException {
        CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(dir);
        CopyOnWriteFSPath parent = cowPath.getParent();
        if (parent == null) {
            throw new CowFSFileAlreadyExistsException(cowPath.toString());
        }

        DirectoryNode parentFolder = this.fs.fileTree().directoryByPath(parent);
        String folderName = Objects.requireNonNull(cowPath.getFileName()).toString();
        if (parentFolder.getChild(folderName) != null) {
            throw new CowFSFileAlreadyExistsException(cowPath.toString());
        }

        parentFolder.addChild(new DirectoryNode(cowPath));
    }

    @Override
    public synchronized void delete(Path path) throws IOException {
        CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(path);
        Node node = this.fs.fileTree().byPath(cowPath);
        if (node.parent == null) {
            throw new CowFSFileSystemException("Can't remove root");
        }

        String name = Objects.requireNonNull(node.name());
        if (node instanceof DirectoryNode directoryNode) {
            if (!directoryNode.children().isEmpty()) {
                throw new CowFSDirectoryNotEmptyException(cowPath.toString());
            }
        } else if (node instanceof FileNode fileNode) {
            fileNode.deleteCopy();
        }

        node.parent.removeChild(name);
    }

    @Override
    public void copy(Path source, Path target, CopyOption... options) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void move(Path source, Path target, CopyOption... options) throws IOException {
        CopyOnWriteFSPath sourceCow = CopyOnWriteFSPath.asCow(source);
        CopyOnWriteFSPath targetCow = CopyOnWriteFSPath.asCow(target);
        if (sourceCow.isRoot()) {
            throw new CowFSFileSystemException(sourceCow + ": can't move root directory");
        }

        boolean replaceExisting = false;

        for (CopyOption option : options) {
            if (option.equals(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(sourceCow.toString(), targetCow.toString(), "CowFs does not support atomic move");
            }

            if (option.equals(StandardCopyOption.REPLACE_EXISTING)) {
                replaceExisting = true;
            }
        }

        Node sourceNode = this.fs.fileTree().byPathOrNull(sourceCow);
        if (sourceNode == null) {
            throw new CowFSNoSuchFileException(sourceCow.toString());
        }

        CopyOnWriteFSPath parent = targetCow.toAbsolutePath().getParent();
        if (parent == null) {
            throw new CowFSFileAlreadyExistsException(targetCow.toString());
        }

        if (this.fs.fileTree().byPathOrNull(parent) instanceof DirectoryNode folderTarget) {
            String newName = Objects.requireNonNull(targetCow.getFileName()).toString();
            Node oldChild = folderTarget.getChild(newName);
            if (oldChild != null) {
                if (oldChild.equals(sourceNode)) {
                    return;
                }

                if (!replaceExisting) {
                    throw new CowFSFileAlreadyExistsException(targetCow.toString());
                }

                folderTarget.removeChild(newName);
            }

            Objects.requireNonNull(sourceNode.parent).removeChild(Objects.requireNonNull(sourceNode.name()));
            sourceNode.setPath(targetCow);
            folderTarget.addChild(sourceNode);
        } else {
            throw new CowFSNoSuchFileException(targetCow.toString());
        }
    }

    @Override
    public boolean isSameFile(Path path, Path path2) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isHidden(Path path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public FileStore getFileStore(Path path) {
        throw new UnsupportedOperationException();
    }

    @Override
    public synchronized void checkAccess(Path path, AccessMode... modes) throws IOException {
        CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(path);
        Node node = this.fs.fileTree().byPath(cowPath);

        Path checkPath = switch (node) {
            case DirectoryNode var9 -> this.fs.tmpDirectory();
            case FileNode file -> file.storagePath();
            default -> throw new MatchException(null, null);
        };
        checkPath.getFileSystem().provider().checkAccess(checkPath, modes);
    }

    @Override
    public synchronized <V extends FileAttributeView> @Nullable V getFileAttributeView(Path path, Class<V> type, LinkOption... options) {
        final CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(path);
        Node node = this.fs.fileTree().byPathOrNull(cowPath);

        return (V)(switch (node) {
            case null -> type == BasicFileAttributeView.class ? new BasicFileAttributeView() {
                @Override
                public String name() {
                    return "basic";
                }

                @Override
                public BasicFileAttributes readAttributes() throws IOException {
                    throw new CowFSNoSuchFileException(cowPath.toString());
                }

                @Override
                public void setTimes(FileTime lastModifiedTime, FileTime lastAccessTime, FileTime createTime) throws IOException {
                    throw new CowFSNoSuchFileException(cowPath.toString());
                }
            } : null;
            case DirectoryNode var9 -> type == BasicFileAttributeView.class ? DUMMY_DIRECTORY_VIEW : null;
            case FileNode file -> Files.getFileAttributeView(file.storagePath(), type, options);
            default -> throw new MatchException(null, null);
        });
    }

    @Override
    public synchronized <A extends BasicFileAttributes> A readAttributes(Path path, Class<A> type, LinkOption... options) throws IOException {
        CopyOnWriteFSPath cowPath = CopyOnWriteFSPath.asCow(path);
        Node node = this.fs.fileTree().byPath(cowPath);

        return (A)(switch (node) {
            case DirectoryNode var9 -> DummyFileAttributes.DIRECTORY;
            case FileNode file -> Files.readAttributes(file.storagePath(), type, options);
            default -> throw new MatchException(null, null);
        });
    }

    @Override
    public Map<String, Object> readAttributes(Path path, String attributes, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setAttribute(Path path, String attribute, Object value, LinkOption... options) {
        throw new UnsupportedOperationException();
    }

    public synchronized CopyOnWriteFSPath getRealPath(CopyOnWriteFSPath path) throws CowFSNoSuchFileException {
        return this.fs.fileTree().byPath(path.toAbsolutePath()).path;
    }

    @FunctionalInterface
    private interface ChannelFactory<C> {
        C newChannel(Path path, Set<? extends OpenOption> options, FileAttribute<?>... attrs) throws IOException;
    }
}
