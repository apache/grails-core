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
package org.grails.plugins.web.mime

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary

import grails.config.Config
import grails.config.Settings
import grails.core.GrailsApplication
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeProvider
import grails.web.mime.MimeTypeResolver
import grails.web.mime.MimeUtility
import org.grails.web.mime.DefaultMimeTypeResolver
import org.grails.web.mime.DefaultMimeUtility

/**
 * Configuration for Codecs
 *
 * @author graemerocher
 * @since 4.0
 */
@Configuration
@CompileStatic
class MimeTypesConfiguration {

    private final GrailsApplication grailsApplication
    private final List<MimeTypeProvider> mimeTypeProviders

    MimeTypesConfiguration(GrailsApplication grailsApplication, List<MimeTypeProvider> mimeTypeProviders) {
        this.grailsApplication = grailsApplication
        this.mimeTypeProviders = mimeTypeProviders
    }

    @Bean('mimeTypesHolder')
    @Primary
    MimeTypesHolder mimeTypesHolder() {
        final MimeType[] mimeTypes = grailsApplication.mainContext.getBean('mimeTypes', MimeType[])
        return new MimeTypesHolder(mimeTypes)
    }

    @Bean('mimeTypes')
    @Primary
    MimeType[] mimeTypes() {
        final Config config = grailsApplication.getConfig()
        final Map<CharSequence, Object> mimeConfig = getMimeConfig(config)

        List<MimeType> mimes = new ArrayList<>()
        if (mimeConfig == null || mimeConfig.isEmpty()) {
            // No user configuration: fall back to the built-in framework defaults
            for (MimeType defaultMimeType : MimeType.createDefaults()) {
                mimes.add(defaultMimeType)
            }
        }
        else {
            // A declared grails.mime.types replaces the defaults by default (the historical behaviour),
            // keeping its declared order so the first entry remains the default format. Set
            // grails.mime.mergeDefaults=true to instead merge the declared types over the defaults.
            final boolean mergeDefaults = config.getProperty(Settings.MIME_TYPES_MERGE_DEFAULTS, Boolean, false)
            Set<String> declaredExtensions = new HashSet<>()
            for (Map.Entry<CharSequence, Object> entry : mimeConfig.entrySet()) {
                final String key = entry.getKey().toString()
                declaredExtensions.add(key)
                final Object v = entry.getValue()
                if (v instanceof List) {
                    List list = (List) v
                    for (Object i : list) {
                        mimes.add(new MimeType(i.toString(), key))
                    }
                }
                else {
                    mimes.add(new MimeType(v.toString(), key))
                }
            }
            if (mergeDefaults) {
                // Append the framework defaults for any extension the user did not declare
                for (MimeType defaultMimeType : MimeType.createDefaults()) {
                    if (!declaredExtensions.contains(defaultMimeType.extension)) {
                        mimes.add(defaultMimeType)
                    }
                }
            }
        }

        processProviders(mimes, this.mimeTypeProviders)
        final Map<String, MimeTypeProvider> childTypes = grailsApplication.mainContext.getBeansOfType(MimeTypeProvider)
        processProviders(mimes, childTypes.values())

        return mimes.toArray(new MimeType[0])
    }

    @Bean('grailsMimeUtility')
    @Primary
    protected MimeUtility mimeUtility(MimeTypesHolder mimeTypesHolder) {
        return new DefaultMimeUtility(mimeTypesHolder.mimeTypes)
    }

    @Bean('mimeTypeResolver')
    @Primary
    protected MimeTypeResolver mimeTypeResolver() {
        return new DefaultMimeTypeResolver()
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected Map<CharSequence, Object> getMimeConfig(Config config) {
        return config.getProperty(Settings.MIME_TYPES, Map)
    }

    private void processProviders(List<MimeType> mimes, Iterable<MimeTypeProvider> mimeTypeProviders) {
        for (MimeTypeProvider mimeTypeProvider : mimeTypeProviders) {
            for (MimeType mimeType : mimeTypeProvider.getMimeTypes()) {
                if (!mimes.contains(mimeType)) {
                    mimes.add(mimeType)
                }
            }
        }
    }

}
