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
package org.grails.forge.feature.grails

import groovy.transform.CompileStatic
import org.springframework.stereotype.Component
import org.grails.forge.application.ApplicationType
import org.grails.forge.application.generator.GeneratorContext
import org.grails.forge.build.dependencies.Dependency
import org.grails.forge.feature.Category
import org.grails.forge.feature.DefaultFeature
import org.grails.forge.feature.Feature
import org.grails.forge.options.Options
import org.grails.forge.template.URLTemplate
import org.grails.forge.util.NameUtils

@Component
@CompileStatic
class GrailsDefaultPlugins implements DefaultFeature {

    private static final List<String> LOCALES = ['cs', 'da', 'de', 'es', 'fr', 'it', 'ja', 'nb', 'nl', 'pl', 'pt_BR', 'pt_PT',
                                                 'ru', 'sk', 'sv', 'th', 'zh_CN', 'zh_TW'].asImmutable()

    @Override
    boolean shouldApply(ApplicationType applicationType, Options options, Set<Feature> selectedFeatures) {
        return true
    }

    @Override
    String getName() {
        return 'grails-dependencies'
    }

    @Override
    boolean isVisible() {
        return false
    }

    @Override
    boolean supports(ApplicationType applicationType) {
        return true
    }

    @Override
    void apply(GeneratorContext generatorContext) {
        Arrays.asList('rest-transforms', 'databinding', 'services', 'url-mappings', 'interceptors')
                .forEach((artifact) -> {
                    generatorContext.addDependency(Dependency.builder()
                            .groupId('org.apache.grails')
                            .artifactId('grails-' + artifact)
                            .implementation())
                })
        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader()
        // An application has the bundles under the base name messages. A plugin has them under its own name, the
        // only base name the build of a plugin takes (generateI18nDescriptor): Spring resolves a base name to the
        // first match on the class path, so messages.properties of a plugin and of the application that uses it
        // would shadow one another
        String baseName = bundleBaseName(generatorContext)
        generatorContext.addTemplate('messages_properties',
                new URLTemplate("grails-app/i18n/${baseName}.properties", classLoader.getResource('i18n/messages.properties'), false))
        for (String locale : LOCALES) {
            generatorContext.addTemplate("messages_${locale}_properties",
                    new URLTemplate("grails-app/i18n/${baseName}_${locale}.properties", classLoader.getResource("i18n/messages_${locale}.properties")))
        }
    }

    @Override
    String getCategory() {
        return Category.SERVER
    }

    /**
     * @return the base name of the message bundles of the project: {@code messages} for an application, and the
     * name of the plugin, as the framework derives it from the name of the plugin class, for a plugin
     */
    static String bundleBaseName(GeneratorContext generatorContext) {
        ApplicationType applicationType = generatorContext.getApplicationType()
        if (applicationType.isPlugin()) {
            return NameUtils.getPluginName(generatorContext.getProject().getClassName() + 'GrailsPlugin.groovy')
        }
        return 'messages'
    }
}
