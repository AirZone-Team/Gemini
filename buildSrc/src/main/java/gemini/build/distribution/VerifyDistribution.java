package gemini.build.distribution;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;

public abstract class VerifyDistribution extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    @TaskAction
    public void verify() throws IOException {
        DistributionVerifier.verify(getArchiveFile().get().getAsFile().toPath());
        getLogger().lifecycle("Verified installable distribution {}", getArchiveFile().get().getAsFile());
    }
}
