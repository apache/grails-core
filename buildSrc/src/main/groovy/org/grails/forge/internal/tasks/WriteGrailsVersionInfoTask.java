/*
 * Copyright 2003-2021 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.internal.tasks;

import groovy.xml.XmlSlurper;
import groovy.xml.slurpersupport.GPathResult;
import groovy.xml.slurpersupport.NodeChild;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.result.ArtifactResolutionResult;
import org.gradle.api.artifacts.result.ComponentArtifactsResult;
import org.gradle.api.artifacts.result.ResolvedArtifactResult;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.maven.MavenModule;
import org.gradle.maven.MavenPomArtifact;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

@CacheableTask
public abstract class WriteGrailsVersionInfoTask extends DefaultTask {
    @Input
    public abstract Property<String> getVersion();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDirectory();

    public WriteGrailsVersionInfoTask() {
        getOutputs().doNotCacheIf("snapshot version", spec -> getVersion().get().endsWith("SNAPSHOT"));
    }

    @TaskAction
    public void writeVersionInfo() throws IOException {
        Map<String, String> props = generateProperties();
        try (OutputStream out = Files.newOutputStream(getOutputDirectory().file("grails-versions.properties").get().getAsFile().toPath())) {
            for (Map.Entry<String, String> entry : props.entrySet()) {
                String line = entry.getKey() + "=" + entry.getValue() + "\n";
                out.write(line.getBytes(StandardCharsets.ISO_8859_1));
            }
        }
    }

    private Map<String, String> generateProperties() {
        ArtifactResolutionResult result = getProject().getDependencies().createArtifactResolutionQuery()
                .forModule("org.apache.grails", "grails-bom", getVersion().get())
                .withArtifacts(MavenModule.class, MavenPomArtifact.class)
                .execute();
        Map<String, String> props = new TreeMap<>();
        props.put("grails.version", getVersion().get());
        for (ComponentArtifactsResult component : result.getResolvedComponents()) {
            component.getArtifacts(MavenPomArtifact.class).forEach(artifact -> {
                if (artifact instanceof ResolvedArtifactResult) {
                    ResolvedArtifactResult resolved = (ResolvedArtifactResult) artifact;
                    GPathResult pom = null;
                    try {
                        pom = new XmlSlurper().parse(resolved.getFile());
                    } catch (IOException | SAXException | ParserConfigurationException e) {
                        // ignore
                    }
                    ((GPathResult) pom.getProperty("properties")).children().forEach(child -> {
                        NodeChild node = (NodeChild) child;
                        props.put(node.name(), node.text());
                    });
                }
            });
        }
        return props;
    }
}
