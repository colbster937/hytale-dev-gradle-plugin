package net.janrupf.gradle.hytale.dev.tasks;

import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.*;
import org.gradle.work.DisableCachingByDefault;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Properties;

/**
 * Task that runs before a Hytale server is started.
 */
@DisableCachingByDefault(because = "Not worth caching and can't be cached reliably")
public abstract class PrepareHytaleServerRunTask extends DefaultTask {
    @Internal
    public abstract DirectoryProperty getWorkingDirectory();

    @Input
    public abstract Property<String> getMainClassName();

    @InputFiles
    @Classpath
    public abstract ConfigurableFileCollection getClasspath();

    @OutputFile
    public abstract RegularFileProperty getAgentConfigurationFile();

    @TaskAction
    public void prepare() throws IOException {
        Files.createDirectories(getWorkingDirectory().get().getAsFile().toPath());

        var properties = new Properties();
        properties.setProperty("classpath", encodeClasspath(getClasspath()));
        properties.setProperty("mainClassName", getMainClassName().get());

        try (var writer = Files.newBufferedWriter(
                getAgentConfigurationFile().get().getAsFile().toPath()
        )) {
            properties.store(writer, "Hytale Dev Agent Configuration");
        }
    }

    private String encodeClasspath(FileCollection files) {
        var base64Encoder = Base64.getEncoder();

        var classpathBuilder = new StringBuilder();

        for (var file : files) {
            if (!classpathBuilder.isEmpty()) {
                classpathBuilder.append(",");
            }
            classpathBuilder.append(base64Encoder.encodeToString(
                    file.toURI().toString().getBytes(StandardCharsets.UTF_8)
            ));
        }

        return classpathBuilder.toString();
    }
}
