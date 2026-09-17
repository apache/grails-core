/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.forge.cli;

import org.springframework.context.ApplicationContext;
import org.grails.forge.application.ApplicationType;
import org.grails.forge.feature.AvailableFeatures;
import org.grails.forge.feature.DefaultFeature;
import org.grails.forge.feature.Feature;
import org.grails.forge.feature.FeatureRegistry;
import org.grails.forge.io.ConsoleOutput;
import org.grails.forge.io.FileSystemOutputHandler;
import org.grails.forge.options.JdkVersion;
import org.grails.forge.options.Language;
import org.grails.forge.options.Options;
import org.grails.forge.options.DevelopmentReloading;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

public class CodeGenConfig {

    private ApplicationType applicationType;
    private String defaultPackage;
    private DevelopmentReloading reloading;
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

    public DevelopmentReloading getReloading() {
        return reloading;
    }

    public void setReloading(DevelopmentReloading reloading) {
        this.reloading = reloading;
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

    public static CodeGenConfig load(ApplicationContext beanContext, ConsoleOutput consoleOutput) {
        try {
            return load(beanContext, FileSystemOutputHandler.getDefaultBaseDirectory(), consoleOutput);
        } catch (IOException e) {
            return null;
        }
    }

    public static CodeGenConfig load(ApplicationContext beanContext, File directory, ConsoleOutput consoleOutput) {

        File grailsCli = new File(directory, "grails-forge-cli.yml");

        if (!grailsCli.exists()) {
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
                CodeGenConfig codeGenConfig = new CodeGenConfig();
                if (map.get("applicationType") != null) {
                    codeGenConfig.setApplicationType(ApplicationType.valueOf(map.get("applicationType").toString().toUpperCase(Locale.ENGLISH).replace('-', '_')));
                }
                if (map.get("defaultPackage") != null) {
                    codeGenConfig.setDefaultPackage(map.get("defaultPackage").toString());
                }
                if (map.get("reloading") != null) {
                    String reloading = map.get("reloading").toString();
                    codeGenConfig.setReloading(Arrays.stream(DevelopmentReloading.values())
                            .filter(value -> value.getName().equals(reloading))
                            .findFirst()
                            .orElseThrow(() -> new IllegalArgumentException("Unknown development reloading option: " + reloading)));
                }
                if (map.get("sourceLanguage") != null) {
                    codeGenConfig.setSourceLanguage(Language.valueOf(map.get("sourceLanguage").toString().toUpperCase(Locale.ENGLISH)));
                }
                if (map.get("features") instanceof List) {
                    List<String> features = new ArrayList<>();
                    for (Object feature : (List<?>) map.get("features")) {
                        features.add(feature.toString());
                    }
                    codeGenConfig.setFeatures(features);
                }

                if (map.containsKey("testFramework") && !map.containsKey("reloading")) {
                    codeGenConfig.setReloading(DevelopmentReloading.NONE);
                }

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

                    AvailableFeatures availableFeatures = beanContext.getBean(FeatureRegistry.class)
                            .availableFeatures(codeGenConfig.getApplicationType());

                    codeGenConfig.setFeatures(availableFeatures.getAllFeatures()
                            .filter(f -> f instanceof DefaultFeature)
                            .map(DefaultFeature.class::cast)
                            .filter(f -> f.shouldApply(
                                    codeGenConfig.getApplicationType(),
                                    new Options(codeGenConfig.getReloading(), JdkVersion.DEFAULT_OPTION),
                                    new HashSet<>()))
                            .map(Feature::getName)
                            .collect(Collectors.toList()));

                    consoleOutput.warning("This project is using Grails Forge CLI v2 but is still using the v1 grails-forge-cli.yml format");
                    consoleOutput.warning("To replace the configuration with the new format, run `grails update-cli-config`");
                }

                return codeGenConfig;
            } catch (IOException e) { }
        }
        return null;
    }
}
