/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.forge.cli;

import io.micronaut.context.BeanContext;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanIntrospection;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.feature.AvailableFeatures;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.FileSystemOutputHandler;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Language;
import org.grails.forge.options.Options;
import org.grails.forge.options.TestFramework;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

@Introspected
public class CodeGenConfig {

    private ApplicationType applicationType;
    private String defaultPackage;
    private TestFramework testFramework;
    private Language sourceLanguage;
    private List<String> features;

    private boolean legacy;

    public ApplicationType getApplicationType() {
        return applicationType;
    }

    public void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public String getDefaultPackage() {
        return defaultPackage;
    }

    public void setDefaultPackage(String defaultPackage) {
        this.defaultPackage = defaultPackage;
    }

    public TestFramework getTestFramework() {
        return testFramework;
    }

    public void setTestFramework(TestFramework testFramework) {
        this.testFramework = testFramework;
    }

    public Language getSourceLanguage() {
        return sourceLanguage;
    }

    public void setSourceLanguage(Language sourceLanguage) {
        this.sourceLanguage = sourceLanguage;
    }

    public List<String> getFeatures() {
        return features;
    }

    public void setFeatures(List<String> features) {
        this.features = features;
    }

    public boolean isLegacy() {
        return legacy;
    }

    public static CodeGenConfig load(BeanContext beanContext, ConsoleOutput consoleOutput) {
        try {
            return load(beanContext, FileSystemOutputHandler.getDefaultBaseDirectory(), consoleOutput);
        } catch (IOException e) {
            return null;
        }
    }

    public static CodeGenConfig load(BeanContext beanContext, File directory, ConsoleOutput consoleOutput) {

        File grailsCli = new File(directory, "grails-forge-cli.yml");

        if (!grailsCli.exists()) {
            // backwards compatibility for grails-cli.yml
            grailsCli = new File(directory, "grails-cli.yml");
        }

        if (grailsCli.exists()) {
            try (InputStream inputStream = Files.newInputStream(grailsCli.toPath())) {
                Yaml yaml = new Yaml();
                Map<String, Object> map = new LinkedHashMap<>();
                Iterable<Object> objects = yaml.loadAll(inputStream);
                Iterator<Object> i = objects.iterator();
                if (i.hasNext()) {
                    while (i.hasNext()) {
                        Object object = i.next();
                        if (object instanceof Map) {
                            map.putAll((Map) object);
                        }
                    }
                }
                BeanIntrospection<CodeGenConfig> introspection = BeanIntrospection.getIntrospection(CodeGenConfig.class);
                CodeGenConfig codeGenConfig = introspection.instantiate();
                introspection.getBeanProperties().forEach(bp -> {
                    Object value = map.get(bp.getName());
                    if (value != null) {
                        bp.convertAndSet(codeGenConfig, value);
                    }
                });

                if (map.containsKey("profile")) {
                    codeGenConfig.legacy = true;
                    String profile = map.get("profile").toString();
                    if (profile.equals("web")) {
                        codeGenConfig.setApplicationType(ApplicationType.WEB);
                    } else if (profile.equals("rest-api")) {
                        codeGenConfig.setApplicationType(ApplicationType.REST_API);
                    } else if (profile.equals("plugin")) {
                        codeGenConfig.setApplicationType(ApplicationType.PLUGIN);
                    } else if (profile.equals("web-plugin")) {
                        codeGenConfig.setApplicationType(ApplicationType.WEB_PLUGIN);
                    } else {
                        return null;
                    }

                    AvailableFeatures availableFeatures = beanContext.getBean(AvailableFeatures.class, Qualifiers.byName(codeGenConfig.getApplicationType().getName()));

                    codeGenConfig.setFeatures(availableFeatures.getAllFeatures()
                            .filter(f -> f instanceof DefaultFeature)
                            .map(DefaultFeature.class::cast)
                            .filter(f -> f.shouldApply(
                                    codeGenConfig.getApplicationType(),
                                    new Options(codeGenConfig.getTestFramework(), JdkVersion.DEFAULT_OPTION),
                                    new HashSet<>()))
                            .map(Feature::getName)
                            .collect(Collectors.toList()));

                    consoleOutput.warning("This project is using Grails CLI v2 but is still using the v1 grails-forge-cli.yml format");
                    consoleOutput.warning("To replace the configuration with the new format, run `grails update-cli-config`");
                }

                return codeGenConfig;
            } catch (IOException e) { }
        }
        return null;
    }
}
