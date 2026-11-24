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
package org.grails.forge.build.gradle

import groovy.transform.CompileStatic
import jakarta.annotation.Nonnull
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Coordinate
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.build.dependencies.DependencyCoordinate
import org.grails.forge.build.dependencies.DependencyExclusion

import static org.grails.forge.build.gradle.GradleConfiguration.INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES

@CompileStatic
class GradleDependency extends DependencyCoordinate {

    public static final Comparator<GradleDependency> COMPARATOR = (o1, o2) -> {
        int comparison = Integer.compare(o1.getOrder(), o2.getOrder())
        if (comparison != 0) {
            return comparison
        }
        comparison = Integer.compare(o1.getConfiguration().getOrder(), o2.getConfiguration().getOrder())
        if (comparison != 0) {
            return comparison
        }
        return Coordinate.COMPARATOR.compare(o1, o2)
    }

    private final List<DependencyExclusion> exclusions

    private final List<String> capabilities

    @Nonnull
    private final GradleConfiguration gradleConfiguration

    GradleDependency(@Nonnull Dependency dependency,
                            @Nonnull GeneratorContext generatorContext) {
        super(dependency)
        gradleConfiguration = GradleConfiguration.of(
            dependency.getScope(),
            generatorContext.getDevelopmentReloading()
        ).orElseThrow(() ->
            new IllegalArgumentException(String.format('Cannot map the dependency scope: [%s] to a Gradle specific scope', dependency.getScope())))
        this.exclusions = dependency.getExclusions()
        this.capabilities = dependency.getCapabilities()
    }

    @Nonnull
    GradleConfiguration getConfiguration() {
        return gradleConfiguration
    }

    /**
     * @return the transitive dependencies this dependency is declared without
     */
    @Nonnull
    List<DependencyExclusion> getExclusions() {
        return exclusions
    }

    /**
     * @return the capabilities, each {@code group:name}, that select the variant of the dependency
     */
    @Nonnull
    List<String> getCapabilities() {
        return capabilities
    }

    @Override
    boolean equals(Object o) {
        if (this.is(o)) {
            return true
        }
        if (o == null || getClass() != o.getClass()) {
            return false
        }
        if (!super.equals(o)) {
            return false
        }
        GradleDependency that = (GradleDependency) o

        return Objects.equals(gradleConfiguration, that.gradleConfiguration)
    }

    @Override
    int hashCode() {
        int result = super.hashCode()
        result = 31 * result + gradleConfiguration.hashCode()
        return result
    }

    @Nonnull
    String toSnippet() {
        if (!exclusions.isEmpty() || !capabilities.isEmpty()) {
            return withComment(toSnippetWithClosure())
        }
        String optionalSpace = gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES ? '' : ' '
        String snippet = gradleConfiguration.getConfigurationName() + optionalSpace
        if (isPom() && isEnforced()) {
            snippet += 'enforcedPlatform('
        } else if (isPom()) {
            snippet += 'platform('
        } else if (gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES) {
            snippet += '('
        }
        snippet += '"' + getGroupId() + ':' + getArtifactId() +
            (getVersion() != null ? (':' + getVersion()) : '') + '"'
        if (isPom() || gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES) {
            snippet += ')'
        }
        return withComment(snippet)
    }

    /**
     * A dependency with exclusions or capabilities is declared with parentheses and a closure, on one line so
     * that it needs no indentation of its own: {@code implementation("g:a") { exclude group: "x", module: "y" }},
     * and the capabilities first, {@code implementation("g:a") { capabilities { requireCapability("g:a-b") } }}.
     */
    private String toSnippetWithClosure() {
        boolean testFixtures = gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES
        String notation = '"' + getGroupId() + ':' + getArtifactId() +
            (getVersion() != null ? (':' + getVersion()) : '') + '"'
        if (isPom() && isEnforced()) {
            notation = 'enforcedPlatform(' + notation + ')'
        } else if (isPom()) {
            notation = 'platform(' + notation + ')'
        } else if (testFixtures) {
            notation = 'testFixtures(' + notation + ')'
        }
        String configurationName = gradleConfiguration.getConfigurationName()
        if (testFixtures) {
            configurationName = configurationName.substring(0, configurationName.indexOf(' '))
        }
        List<String> statements = []
        if (!capabilities.isEmpty()) {
            String required = capabilities.collect { String capability -> 'requireCapability("' + capability + '")' }.join('; ')
            statements << 'capabilities { ' + required + ' }'
        }
        statements.addAll(exclusions.collect { DependencyExclusion exclusion ->
            'exclude group: "' + exclusion.getGroupId() + '"' +
                (exclusion.getArtifactId() != null ? ', module: "' + exclusion.getArtifactId() + '"' : '')
        })
        return configurationName + '(' + notation + ') { ' + statements.join('; ') + ' }'
    }

    private String withComment(String snippet) {
        if (getArtifactId() == 'spring-boot-devtools') {
            return snippet + ' // Spring Boot DevTools may cause performance slowdowns or compatibility issues on larger applications'
        }
        return snippet
    }
}
