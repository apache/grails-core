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
package org.grails.forge.build.dependencies;


import io.micronaut.core.annotation.NonNull;
import org.grails.forge.build.gradle.GradleRepository;

import java.util.Collection;

public interface DependencyContext {

    @NonNull
    Collection<Dependency> getDependencies();

    @NonNull
    Collection<Dependency> getBuildscriptDependencies();

    @NonNull
    Collection<GradleRepository> getRepositories();

    @NonNull
    Collection<GradleRepository> getBuildRepositories();

    void addDependency(@NonNull Dependency dependency);

    void addBuildscriptDependency(@NonNull Dependency dependency);

    default void addDependency(@NonNull Dependency.Builder dependency) {
        addDependency(dependency.build());
    }

    default void addBuildscriptDependency(@NonNull Dependency.Builder dependency) {
        addBuildscriptDependency(dependency.build());
    }

}
