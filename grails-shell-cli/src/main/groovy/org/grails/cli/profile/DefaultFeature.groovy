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

package org.grails.cli.profile

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.eclipse.aether.graph.Dependency
import org.grails.config.NavigableMap
import org.grails.io.support.Resource
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

import static org.grails.cli.profile.ProfileUtil.createDependency

/**
 * Default implementation of the {@link Feature} interface
 *
 * @author Graeme Rocher
 * @since 3.1
 */
@EqualsAndHashCode(includes = ['name'])
@ToString(includes = ['profile', 'name'])
@CompileStatic
class DefaultFeature implements Feature {
    final Profile profile
    final String name
    final Resource location
    final NavigableMap configuration = new NavigableMap()
    final List<Dependency> dependencies = []
    final List<String> buildPlugins
    final List<String> buildRepositories

    DefaultFeature(Profile profile, String name, Resource location) {
        this.profile = profile
        this.name = name
        this.location = location
        def featureYml = location.createRelative("feature.yml")
        Map<String, Object> featureConfig = new Yaml(new SafeConstructor(new LoaderOptions())).<Map<String, Object>>load(featureYml.getInputStream())
        configuration.merge(featureConfig)
        def dependenciesConfig = configuration.get("dependencies")

        if(dependenciesConfig instanceof List) {
            for(entry in ((List) dependenciesConfig)) {
                if (entry instanceof Map) {
                    def scope = (String) entry.scope
                    def os = entry.os
                    if (os && !isSupportedOs(os.toString())) {
                        continue
                    }
                    String coords = (String) entry.coords
                    Dependency dependency = createDependency(coords, scope, entry)
                    dependencies.add(dependency)
                }
            }
        }
        this.buildPlugins = (List<String>)configuration.get("build.plugins", [])
        this.buildRepositories = (List<String>) configuration.get("build.repositories", [])
    }

    @Override
    String getDescription() {
        configuration.get("description", '').toString()
    }

    static boolean isSupportedOs(String os) {
        os = os.toLowerCase(Locale.ENGLISH).trim()
        String osName = System.getProperty("os.name")?.toLowerCase(Locale.ENGLISH) ?: "unix"
        switch (os) {
            case "windows":
                return osName.contains("windows")
            case "osx":
                return osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx")
            case "unix":
                return osName.contains("mac os x") || osName.contains("darwin") || osName.contains("osx") ||
                        osName.contains("sunos") || osName.contains("solaris") || osName.contains("linux") ||
                        osName.contains("freebsd")
            default:
                return false
        }
    }

}
