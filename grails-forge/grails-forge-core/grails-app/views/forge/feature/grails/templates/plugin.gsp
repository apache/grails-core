%{--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
--}%
<%@ page trimLogicLines="true" expressionCodec="none" %>
<%@ page import="org.grails.forge.application.Project" %>
<%@ model="Project project" %>
package ${project.getPackageName()}

import org.springframework.beans.factory.BeanRegistrar
import org.springframework.beans.factory.BeanRegistry
import org.springframework.core.env.Environment

import grails.plugins.Plugin
import grails.plugins.metadata.GrailsPluginDescriptor

/**
 * The ${project.getNaturalName()} plugin. Its name, ordering and the rest of its metadata are declared by
 * the annotation; its version is the project version. The published POM carries the description, license
 * and developers, from the grailsPublish block in build.gradle.
 */
@GrailsPluginDescriptor(
        // Plugins this one loads after, or needs: loadAfter = ['controllers'], dependsOn = ['core']
        // Artefact classes left out of the plugin's descriptor: pluginExcludes = ['com/example/demo/**']
)
class ${project.getClassName()}GrailsPlugin extends Plugin {

    BeanRegistrar beanRegistrar() {
        return { BeanRegistry registry, Environment environment ->
            // TODO Register runtime Spring beans (optional)
        } as BeanRegistrar
    }

    void doWithDynamicMethods() {
        // TODO Implement registering dynamic methods to classes (optional)
    }

    void doWithApplicationContext() {
        // TODO Implement post initialization spring config (optional)
    }

    void onChange(Map<String, Object> event) {
        // TODO Implement code that is executed when any artefact that this plugin is
        // watching is modified and reloaded. The event contains: event.source,
        // event.application, event.manager, event.ctx, and event.plugin.
    }

    void onConfigChange(Map<String, Object> event) {
        // TODO Implement code that is executed when the project configuration changes.
        // The event is the same as for 'onChange'.
    }

    void onShutdown(Map<String, Object> event) {
        // TODO Implement code that is executed when the application shuts down (optional)
    }
}
