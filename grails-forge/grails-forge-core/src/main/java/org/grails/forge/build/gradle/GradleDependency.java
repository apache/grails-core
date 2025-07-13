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
package org.grails.forge.build.gradle;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.order.OrderUtil;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.dependencies.Coordinate;
import org.grails.forge.build.dependencies.Dependency;
import org.grails.forge.build.dependencies.DependencyCoordinate;
import org.grails.forge.options.Language;
import org.grails.forge.template.Writable;

import java.util.Comparator;
import java.util.Objects;

import static org.grails.forge.build.gradle.GradleConfiguration.INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES;

public class GradleDependency extends DependencyCoordinate {

    public static final Comparator<GradleDependency> COMPARATOR = (o1, o2) -> {
        int comparison = OrderUtil.COMPARATOR.compare(o1, o2);
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(o1.getConfiguration().getOrder(), o2.getConfiguration().getOrder());
        if (comparison != 0) {
            return comparison;
        }
        return Coordinate.COMPARATOR.compare(o1, o2);
    };

    private final Writable extension;

    @NonNull
    private final GradleConfiguration gradleConfiguration;

    public GradleDependency(@NonNull Dependency dependency,
                            @NonNull GeneratorContext generatorContext) {
        this(dependency, generatorContext, null);
    }

    public GradleDependency(@NonNull Dependency dependency,
                            @NonNull GeneratorContext generatorContext,
                            @Nullable Writable extension) {
        super(dependency);
        gradleConfiguration = GradleConfiguration.of(
                dependency.getScope(),
                Language.DEFAULT_OPTION,
                generatorContext.getTestFramework()
        ).orElseThrow(() ->
                new IllegalArgumentException(String.format("Cannot map the dependency scope: [%s] to a Gradle specific scope", dependency.getScope())));
        this.extension = extension;
    }

    @NonNull
    public GradleConfiguration getConfiguration() {
        return gradleConfiguration;
    }

    public Writable getExtension() {
        return extension;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        GradleDependency that = (GradleDependency) o;

        return Objects.equals(gradleConfiguration, that.gradleConfiguration);
    }

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + gradleConfiguration.hashCode();
        return result;
    }

    @NonNull
    public String toSnippet() {
        String optionalSpace = gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES ? "" : " ";
        String snippet = gradleConfiguration.getConfigurationName() + optionalSpace;
        if (isPom()) {
            snippet += "platform(";
        } else if (gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES) {
            snippet += "(";
        }
        snippet += "\"" + getGroupId() + ':' + getArtifactId() +
                (getVersion() != null ? (':' + getVersion()) : "") + "\"";
        if (isPom() || gradleConfiguration == INTEGRATION_TEST_IMPLEMENTATION_TEST_FIXTURES) {
            snippet += ")";
        }
        return snippet;
    }
}
