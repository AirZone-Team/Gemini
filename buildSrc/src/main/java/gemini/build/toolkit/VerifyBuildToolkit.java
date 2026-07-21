package gemini.build.toolkit;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class VerifyBuildToolkit extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    @TaskAction
    public void verify() throws IOException {
        BuildToolkitVerifier.verify(getArchiveFile().get().getAsFile().toPath());
        getLogger().lifecycle("Verified developer buildkit {}", getArchiveFile().get().getAsFile());
    }
}
