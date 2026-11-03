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
package org.grails.cli.compiler.dependencies

import groovy.transform.CompileStatic

/**
 * {@link DependencyManagement} that delegates to one or more {@link DependencyManagement}
 * instances.
 *
 * @author Andy Wilkinson
 * @since 1.3.0
 */
@CompileStatic
class CompositeDependencyManagement implements DependencyManagement {

    private final List<DependencyManagement> delegates

    private final List<Dependency> dependencies = new ArrayList<>()

    CompositeDependencyManagement(DependencyManagement... delegates) {
        this.delegates = Arrays.asList(delegates)
        for (DependencyManagement delegate in delegates) {
            this.dependencies.addAll(delegate.getDependencies())
        }
    }

    @Override
    List<Dependency> getDependencies() {
        return this.dependencies
    }

    @Override
    String getSpringBootVersion() {
        for (DependencyManagement delegate in this.delegates) {
            String version = delegate.getSpringBootVersion()
            if (version != null) {
                return version
            }
        }
        return null
    }

    @Override
    Dependency find(String artifactId) {
        for (DependencyManagement delegate in this.delegates) {
            Dependency found = delegate.find(artifactId)
            if (found != null) {
                return found
            }
        }
        return null
    }

}
