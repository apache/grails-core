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

import java.util.Collections;
import java.util.List;
import java.util.Locale;

import io.micronaut.core.annotation.NonNull;

public enum TestFramework {
    SPOCK;

    public static final TestFramework DEFAULT_OPTION = SPOCK;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }

    @NonNull
    public String getName() {
        return name().toLowerCase(Locale.ENGLISH);
    }

    public String getSourcePath(String path) {
        return Language.GROOVY.getTestSrcDir() + path + getTestFrameworkSuffix() + Language.GROOVY.getExtension();
    }

    public String getIntegrationSourcePath(String path) {
        return Language.GROOVY.getIntegrationSrcDir() + path + getTestFrameworkSuffix() + Language.GROOVY.getExtension();
    }

    public String getTestFrameworkSuffix() {
        return "Spec.";
    }

    /**
     *
     * @return The list of supported languages for a {@link TestFramework}
     */
    public List<Language> getSupportedLanguages() {
        switch (this) {
            case SPOCK:
                return Collections.singletonList(Language.GROOVY);
            default:
                throw new RuntimeException("No list of supported languages have been defined for " + this.getName());
        }
    }

    /**
     *
     * @return The default language for a {@link TestFramework}
     */
    public Language getDefaultLanguage() {
        switch (this) {
            case SPOCK:
                return Language.GROOVY;
            default:
                throw new RuntimeException("No default language have been defined for " + this.getName());
        }
    }

    public org.grails.forge.options.TestFramework toTestFramework() {
        return TestFramework.SPOCK;
    }
}
