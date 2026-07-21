package gemini.build.slang;

import io.github.refux.slang.GeminiSlangCompiler;
import io.github.refux.slang.Stage;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CacheableTask
public abstract class ValidateSlangShaders extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getInputDirectory();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public String getCompilerLibraryVersion() {
        return "0.0.6";
    }

    @Input
    public String getCompilerBuildTag() {
        return "2026.13";
    }

    @TaskAction
    public void validateShaders() throws IOException {
        Path inputRoot = getInputDirectory().get().getAsFile().toPath();
        Path outputRoot = getOutputDirectory().get().getAsFile().toPath();
        CompileSlangShaders.recreateDirectory(outputRoot);
        List<Path> shaders;
        try (var paths = Files.walk(inputRoot)) {
            shaders = paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".vsh")
                            || path.getFileName().toString().endsWith(".fsh"))
                    .sorted()
                    .toList();
        }
        try (GeminiSlangCompiler compiler = new GeminiSlangCompiler()) {
            if (!getCompilerBuildTag().equals(compiler.buildTag())) {
                throw new GradleException("Expected Slang " + getCompilerBuildTag()
                        + " but loaded " + compiler.buildTag());
            }
            for (Path shader : shaders) {
                validateShader(compiler, inputRoot, outputRoot, shader);
            }
        }
        getLogger().lifecycle("Validated {} generated GLSL shaders through Slang's SPIR-V backend", shaders.size());
    }

    private void validateShader(GeminiSlangCompiler compiler, Path inputRoot,
                                Path outputRoot, Path shader) throws IOException {
        Stage stage = shader.getFileName().toString().endsWith(".vsh")
                ? Stage.VERTEX : Stage.FRAGMENT;
        Path relative = inputRoot.relativize(shader);
        List<String> diagnostics = new ArrayList<>();
        try {
            byte[] spirv = compiler.validateGlsl(
                    relative.toString().replace('\\', '_').replace('/', '_').replace('.', '_'),
                    Files.readString(shader, StandardCharsets.UTF_8), stage, diagnostics);
            for (String diagnostic : diagnostics) {
                getLogger().warn("{}", diagnostic.trim());
            }
            Path output = outputRoot.resolve(relative.toString() + ".spv");
            Files.createDirectories(output.getParent());
            Files.write(output, spirv);
        } catch (RuntimeException exception) {
            throw new GradleException("Generated GLSL validation failed for " + relative
                    + " (stage=" + stage + ")\n" + exception.getMessage(), exception);
        }
    }
}
