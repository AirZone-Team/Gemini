package gemini.build.slang;

import io.github.refux.slang.GeminiSlangCompiler;
import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@CacheableTask
public abstract class CompileSlangShaders extends DefaultTask {
    @InputDirectory
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract DirectoryProperty getSourceDirectory();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getVariantManifest();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    @Input
    public abstract Property<String> getCompilerLibraryVersion();

    @Input
    public abstract Property<String> getCompilerBuildTag();

    @Input
    public String getNormalizerVersion() {
        return MinecraftGlslNormalizer.VERSION;
    }

    @TaskAction
    public void compile() throws IOException {
        Path sourceRoot = getSourceDirectory().get().getAsFile().toPath();
        Path outputRoot = getOutputDirectory().get().getAsFile().toPath();
        recreateDirectory(outputRoot);
        List<SlangManifest.ShaderEntry> entries = SlangManifest.load(
                sourceRoot, getVariantManifest().get().getAsFile().toPath());

        try (GeminiSlangCompiler compiler = new GeminiSlangCompiler()) {
            if (!getCompilerBuildTag().get().equals(compiler.buildTag())) {
                throw new GradleException("Expected Slang " + getCompilerBuildTag().get()
                        + " but loaded " + compiler.buildTag());
            }
            getLogger().lifecycle("Compiling {} Slang shader entries with Slang {}",
                    entries.size(), compiler.buildTag());
            for (SlangManifest.ShaderEntry entry : entries) {
                compileEntry(compiler, entry, outputRoot);
            }
        }
    }

    private void compileEntry(GeminiSlangCompiler compiler, SlangManifest.ShaderEntry entry,
                              Path outputRoot) throws IOException {
        String source = Files.readString(entry.source(), StandardCharsets.UTF_8);
        List<String> diagnostics = new ArrayList<>();
        try {
            byte[] code = compiler.compileSlang(entry.moduleName(), source, "glsl_330",
                    entry.define(), diagnostics);
            for (String diagnostic : diagnostics) {
                getLogger().warn("{}", diagnostic.trim());
            }
            String rawGlsl = new String(code, StandardCharsets.UTF_8);
            MinecraftGlslNormalizer.ShaderStage stage = entry.stage().equals("vertex")
                    ? MinecraftGlslNormalizer.ShaderStage.VERTEX
                    : MinecraftGlslNormalizer.ShaderStage.FRAGMENT;
            String glsl;
            try {
                glsl = MinecraftGlslNormalizer.normalize(rawGlsl, stage);
            } catch (RuntimeException exception) {
                Path debugOutput = getTemporaryDir().toPath().resolve(
                        entry.outputName().replace('/', '_') + entry.extension());
                Files.writeString(debugOutput, rawGlsl, StandardCharsets.UTF_8);
                throw new GradleException(exception.getMessage() + " (raw GLSL: " + debugOutput + ")", exception);
            }
            Path output = outputRoot.resolve("assets/gemini/shaders")
                    .resolve(entry.outputName() + entry.extension());
            Files.createDirectories(output.getParent());
            Files.writeString(output, glsl, StandardCharsets.UTF_8);
        } catch (RuntimeException exception) {
            throw new GradleException("Slang compilation failed for " + entry.source()
                    + " (stage=" + entry.stage() + ", define="
                    + (entry.define() == null ? "<base>" : entry.define()) + ")\n"
                    + exception.getMessage(), exception);
        }
    }

    static void recreateDirectory(Path directory) throws IOException {
        if (Files.exists(directory)) {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
        Files.createDirectories(directory);
    }
}
