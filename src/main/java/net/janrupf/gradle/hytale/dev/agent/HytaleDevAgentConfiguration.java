package net.janrupf.gradle.hytale.dev.agent;

import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;

public class HytaleDevAgentConfiguration {
    private final Provider<RegularFile> agentJar;
    private final Provider<RegularFile> serverJar;

    public HytaleDevAgentConfiguration(Provider<RegularFile> agentJar, Provider<RegularFile> serverJar) {
        this.agentJar = agentJar;
        this.serverJar = serverJar;
    }

    public Provider<RegularFile> getAgentJar() {
        return agentJar;
    }

    public Provider<RegularFile> getServerJar() {
        return serverJar;
    }
}
