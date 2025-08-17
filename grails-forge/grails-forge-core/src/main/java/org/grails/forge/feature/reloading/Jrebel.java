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
package org.grails.forge.feature.reloading;

import jakarta.inject.Singleton;
import org.grails.forge.application.generator.GeneratorContext;
import org.grails.forge.build.gradle.GradlePlugin;

@Singleton
public class Jrebel implements ReloadingFeature {

    @Override
    public String getName() {
        return "jrebel";
    }

    @Override
    public String getTitle() {
        return "JRebel JVM Agent";
    }

    @Override
    public String getDescription() {
        return "Adds support for class reloading with JRebel (requires separate JRebel installation)";
    }

    @Override
    public void apply(GeneratorContext generatorContext) {
        generatorContext.getBuildProperties().addComment("TODO: Replace with agent path from JRebel installation; see documentation");
        generatorContext.getBuildProperties().addComment("rebelAgent=-agentpath:~/bin/jrebel/lib/jrebel6/lib/libjrebel64.dylib");
        generatorContext.addBuildPlugin(GradlePlugin.builder()
                .id("org.zeroturnaround.gradle.jrebel")
                .lookupArtifactId("gradle-jrebel-plugin")
                .build());
        generatorContext.addHelpLink("JRebel Gradle Plugin", "https://plugins.gradle.org/plugin/org.zeroturnaround.gradle.jrebel");
    }

    @Override
    public String getDocumentation() {
        return "https://docs.micronaut.io/latest/guide/index.html#jrebel";
    }
}
