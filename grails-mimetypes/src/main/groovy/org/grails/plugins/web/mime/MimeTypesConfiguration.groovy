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

import java.util.function.Supplier

import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.web.accept.ContentNegotiationManager

import grails.config.Config
import grails.config.Settings
import grails.core.GrailsApplication
import grails.web.mime.MimeType
import grails.web.mime.MimeTypeProvider
import grails.web.mime.MimeTypeResolver
import grails.web.mime.MimeUtility
import org.grails.web.mime.DefaultMimeTypeResolver
import org.grails.web.mime.DefaultMimeUtility
import org.grails.web.mime.GrailsContentNegotiationManagerPostProcessor
import org.grails.web.mime.GrailsContentNegotiationStrategy
import org.grails.web.mime.GrailsMimeTypesWebMvcConfigurer

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
        List<MimeType> mimes = new ArrayList<>(Arrays.asList(configuredMimeTypes(config, getMimeConfig(config))))
        processProviders(mimes, this.mimeTypeProviders)
        final Map<String, MimeTypeProvider> childTypes = grailsApplication.mainContext.getBeansOfType(MimeTypeProvider)
        processProviders(mimes, childTypes.values())

        return mimes.toArray(new MimeType[0])
    }

    /**
     * The MIME types {@code grails.mime.types} declares, or the defaults, without those the {@code MimeTypeProvider}
     * beans add.
     */
    private static MimeType[] configuredMimeTypes(Config config, Map<CharSequence, Object> mimeConfig) {
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

        return mimes.toArray(new MimeType[0])
    }

    @Bean('grailsMimeUtility')
    @Primary
    protected MimeUtility mimeUtility(MimeTypesHolder mimeTypesHolder) {
        return new DefaultMimeUtility(mimeTypesHolder.mimeTypes)
    }

    /**
     * Static, so that the resolver is created without this configuration, whose constructor collects every
     * {@code MimeTypeProvider}, among them the renderers. A renderer reaches the GSP page locator through the renderer
     * registry, and the locator is given this resolver, so creating this configuration for it would close a cycle that
     * no early reference can break: this configuration would be needed while its own constructor is still collecting
     * the renderers.
     */
    @Bean('mimeTypeResolver')
    @Primary
    protected static MimeTypeResolver mimeTypeResolver() {
        return new DefaultMimeTypeResolver()
    }

    /**
     * Static, so that registering the configurer does not create this configuration, whose
     * constructor collects every {@code MimeTypeProvider}, among them the renderers. Spring MVC
     * creates its configurers while its infrastructure beans are created, and a renderer that
     * depends on one of those beans would otherwise close a cycle. For the same reason, the
     * formats it registers with Spring MVC are those of the configuration: those
     * {@code grails.mime.types} declares, or the defaults where it declares none or
     * {@code grails.mime.mergeDefaults} adds them, which Spring MVC's content negotiation manager
     * needs as it is created, and not those a {@code MimeTypeProvider} adds. The strategy it builds resolves formats,
     * with {@code grails.mime.negotiation.spring}, with Spring MVC's content negotiation manager, which is asked for
     * once a request is negotiated, since it is created after the configurer.
     */
    @Bean
    static GrailsMimeTypesWebMvcConfigurer grailsMimeTypesWebMvcConfigurer(
            ObjectProvider<MimeUtility> mimeUtility, GrailsApplication grailsApplication,
            @Qualifier(GrailsContentNegotiationStrategy.MVC_CONTENT_NEGOTIATION_MANAGER)
                    ObjectProvider<ContentNegotiationManager> contentNegotiationManager) {
        // A user agent that is not a regular expression is reported as the application starts, rather than by every
        // request, which builds the strategy once it is first asked for
        GrailsContentNegotiationStrategy.disabledUserAgentPatterns(grailsApplication.config)
        // The MIME types request.format and response.format resolve against as well
        Supplier<ContentNegotiationManager> manager = () -> contentNegotiationManager.getIfAvailable()
        Supplier<GrailsContentNegotiationStrategy> strategy = () ->
                new GrailsContentNegotiationStrategy(mimeUtility.getObject().knownMimeTypes as MimeType[],
                        grailsApplication.config, manager)
        Supplier<MimeType[]> formats = () ->
                configuredMimeTypes(grailsApplication.config, declaredMimeTypes(grailsApplication.config))
        return new GrailsMimeTypesWebMvcConfigurer(strategy, formats)
    }

    /**
     * With {@code grails.mime.negotiation.spring}, has Spring MVC's content negotiation manager apply the
     * {@code grails.mime} rules Grails keeps to the requests a URL mapping handles. Static, as a post-processor is
     * created before the other beans: it reads the setting from the application's configuration as Spring MVC's manager
     * is created, as the strategy of the configurer, which it applies the rules with once a request is negotiated, reads
     * it.
     *
     * @since 9.0
     */
    @Bean
    static GrailsContentNegotiationManagerPostProcessor grailsContentNegotiationManagerPostProcessor(
            ObjectProvider<GrailsApplication> grailsApplication,
            ObjectProvider<GrailsMimeTypesWebMvcConfigurer> grailsMimeTypesWebMvcConfigurer) {
        return new GrailsContentNegotiationManagerPostProcessor(() -> grailsApplication.getObject().config, () ->
                grailsMimeTypesWebMvcConfigurer.getObject().contentNegotiationStrategy)
    }

    /**
     * The MIME types the {@code mimeTypes} bean, and so the application's {@code MimeUtility}, starts from, before those
     * of the {@code MimeTypeProvider} beans. The formats registered with Spring MVC are those {@code grails.mime.types}
     * declares, whatever a subclass returns here, since they are read before this configuration may be created (see
     * {@link #grailsMimeTypesWebMvcConfigurer}).
     *
     * @param config the application's configuration
     * @return the MIME types by format, or {@code null} for the defaults
     */
    protected Map<CharSequence, Object> getMimeConfig(Config config) {
        return declaredMimeTypes(config)
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private static Map<CharSequence, Object> declaredMimeTypes(Config config) {
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
