package gemini.build.slang;

import org.gradle.api.model.ObjectFactory;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;

import javax.inject.Inject;

public abstract class GeminiSlangExtension {
    private final DirectoryProperty sourceDirectory;
    private final RegularFileProperty variantManifest;
    private final DirectoryProperty outputDirectory;
    private final DirectoryProperty validationOutputDirectory;

    @Inject
    public GeminiSlangExtension(ObjectFactory objects) {
        sourceDirectory = objects.directoryProperty();
        variantManifest = objects.fileProperty();
        outputDirectory = objects.directoryProperty();
        validationOutputDirectory = objects.directoryProperty();
    }

    public DirectoryProperty getSourceDirectory() {
        return sourceDirectory;
    }

    public RegularFileProperty getVariantManifest() {
        return variantManifest;
    }

    public DirectoryProperty getOutputDirectory() {
        return outputDirectory;
    }

    public DirectoryProperty getValidationOutputDirectory() {
        return validationOutputDirectory;
    }
}
