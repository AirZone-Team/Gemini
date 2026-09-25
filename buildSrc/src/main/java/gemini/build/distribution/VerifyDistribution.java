package gemini.build.distribution;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.file.Path;

public abstract class VerifyDistribution extends DefaultTask {
    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getArchiveFile();

    /**
     * The {@code compileSlangShaders} output. Declared as an input so the
     * shader set the JAR is checked against is hashed, and a shader added or
     * dropped re-runs this task instead of silently passing.
     */
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getGeneratedShaders();

    @TaskAction
    public void verify() throws IOException {
        Path generatedShaders = getGeneratedShaders().get().getAsFile().toPath();
        Path archive = getArchiveFile().get().getAsFile().toPath();
        getLogger().lifecycle("Verifying installable distribution {} against {} generated .vsh/.fsh resources",
                archive.getFileName(), DistributionVerifier.generatedShaderPaths(generatedShaders).size());
        DistributionVerifier.verify(archive, generatedShaders);
    }
}
