package gemini.build.slang;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider;

public final class GeminiSlangPlugin implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        GeminiSlangExtension extension = project.getExtensions().create(
                "geminiSlang", GeminiSlangExtension.class);
        extension.getSourceDirectory().convention(
                project.getLayout().getProjectDirectory().dir("src/main/slang/gemini"));
        extension.getVariantManifest().convention(
                project.getLayout().getProjectDirectory().file("src/main/slang/variants.json"));
        extension.getOutputDirectory().convention(
                project.getLayout().getBuildDirectory().dir("generated/slang"));
        extension.getValidationOutputDirectory().convention(
                project.getLayout().getBuildDirectory().dir("validation/slang/spirv"));

        TaskProvider<CompileSlangShaders> compile = project.getTasks().register(
                "compileSlangShaders", CompileSlangShaders.class, task -> {
                    task.setGroup("build");
                    task.setDescription("Compiles Slang sources and variants to Minecraft-compatible GLSL.");
                    task.getSourceDirectory().set(extension.getSourceDirectory());
                    task.getVariantManifest().set(extension.getVariantManifest());
                    task.getOutputDirectory().set(extension.getOutputDirectory());
                });

        TaskProvider<ValidateSlangShaders> validate = project.getTasks().register(
                "validateSlangShaders", ValidateSlangShaders.class, task -> {
                    task.setGroup("verification");
                    task.setDescription("Checks final GLSL syntax through Slang's SPIR-V backend (not an OpenGL driver test).");
                    task.dependsOn(compile);
                    task.getInputDirectory().set(extension.getOutputDirectory());
                    task.getOutputDirectory().set(extension.getValidationOutputDirectory());
                });

        project.getPluginManager().withPlugin("java", ignored -> {
            project.getTasks().named("processResources").configure(task -> task.dependsOn(compile));
            project.getTasks().named("check").configure(task -> task.dependsOn(validate));
        });
    }
}
