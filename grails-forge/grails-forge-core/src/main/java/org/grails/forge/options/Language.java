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
package org.grails.forge.options;

import io.micronaut.core.annotation.NonNull;
import org.grails.forge.defaults.IncludesDefaults;
import org.grails.forge.defaults.LanguageDefaults;
import org.grails.forge.feature.Feature;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

public enum Language implements IncludesDefaults<LanguageDefaults> {
    GROOVY("groovy", new LanguageDefaults(TestFramework.SPOCK, BuildTool.GRADLE));

    public static final Language DEFAULT_OPTION = GROOVY;

    private final String extension;
    private final LanguageDefaults defaults;

    Language(String extension, LanguageDefaults defaults) {
        this.extension = extension;
        this.defaults = defaults;
    }

    /**
     * @return The extensions
     */
    public String getExtension() {
        return extension;
    }

    public static String[] extensions() {
        return Arrays.stream(values()).map(Language::getExtension).toArray(String[]::new);
    }

    public static String[] srcDirs() {
        return Arrays.stream(values()).map(Language::getSrcDir).toArray(String[]::new);
    }

    public static String[] testSrcDirs() {
        return Arrays.stream(values()).map(Language::getTestSrcDir).toArray(String[]::new);
    }

    public String getSrcDir() {
        return "src/main/" + getName();
    }

    public String getTestSrcDir() {
        return "src/test/" + getName();
    }

    public String getIntegrationSrcDir() {
        return  "src/integration-test/" + getName();
    }

    public String getSourcePath(String path) {
        return getSrcDir() + path + "." + getExtension();
    }

    public String getTestSourcePath(String path) {
        return getTestSrcDir() + path + "." + getExtension();
    }

    public static Language infer(Set<Feature> features) {
        return Language.GROOVY;
    }

    @Override
    public String toString() {
        return getName();
    }

    @NonNull
    public String getName() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    @Override
    public LanguageDefaults getDefaults() {
        return defaults;
    }
}
