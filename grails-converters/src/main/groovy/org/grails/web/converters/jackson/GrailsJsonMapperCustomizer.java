/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.jackson;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import groovy.lang.GString;

import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.ToStringSerializer;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.validation.Errors;

import grails.core.GrailsApplication;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.datastore.mapping.model.MappingContext;

/**
 * Adds Grails-specific serializers to Spring Boot's configured JSON mapper.
 *
 * @since 8.0
 */
public final class GrailsJsonMapperCustomizer implements JsonMapperBuilderCustomizer {

    /** Writer attribute holding the property names to include, as a List or a Map keyed by type. */
    public static final String INCLUDES_ATTRIBUTE = GrailsJsonMapperCustomizer.class.getName() + ".includes";

    /** Writer attribute holding the property names to exclude, as a List or a Map keyed by type. */
    public static final String EXCLUDES_ATTRIBUTE = GrailsJsonMapperCustomizer.class.getName() + ".excludes";

    private final ConcurrentMap<JsonMapper, JsonMapper> grailsMappers = new ConcurrentHashMap<>();
    private final GrailsApplication grailsApplication;
    private final ProxyHandler proxyHandler;

    public GrailsJsonMapperCustomizer() {
        this(null, new DefaultProxyHandler());
    }

    public GrailsJsonMapperCustomizer(GrailsApplication grailsApplication) {
        this(grailsApplication, new DefaultProxyHandler());
    }

    public GrailsJsonMapperCustomizer(GrailsApplication grailsApplication, ProxyHandler proxyHandler) {
        this.grailsApplication = grailsApplication;
        this.proxyHandler = proxyHandler;
    }

    private boolean domainArtefact(Class<?> type) {
        // Decided from the class itself rather than the artefact registry: registry lookups need
        // the Domain handler to have been registered and raise when it has not, whereas this holds
        // as soon as the class is loaded -- which is the point, since GORM is not up yet. The flag
        // lets a proxy be recognised through its domain superclass.
        return DomainClassArtefactHandler.isDomainClass(type, true);
    }

    private MappingContext mappingContext() {
        return this.grailsApplication == null ? null : this.grailsApplication.getMappingContext();
    }

    private boolean booleanProperty(String key, String fallbackKey) {
        if (this.grailsApplication == null) {
            return false;
        }
        boolean fallback = this.grailsApplication.getConfig().getProperty(fallbackKey, Boolean.class, false);
        return this.grailsApplication.getConfig().getProperty(key, Boolean.class, fallback);
    }

    @Override
    public void customize(JsonMapper.Builder builder) {
        SimpleModule module = new SimpleModule("grails-json");
        module.addSerializer(GString.class, ToStringSerializer.instance);
        // Resolve messages at write time, after the application context is ready.
        module.addSerializer(Errors.class, new SpringErrorsJsonSerializer(
                () -> this.grailsApplication == null ? null : this.grailsApplication.getMainContext()));
        builder.addModule(module);
    }

    /**
     * Derives the mapper used by Grails responses without changing Spring MVC's mapper.
     * Domain compatibility uses persistent metadata rather than Jackson's bean property model.
     */
    public JsonMapper forGrails(JsonMapper mapper) {
        return grailsMappers.computeIfAbsent(mapper, source -> {
            JsonMapper.Builder builder = source.rebuild();
            List<JacksonModule> modules = new ArrayList<>();
            builder.withModules(modules::add);
            builder.removeAllModules();
            // Install compatibility first so application serializers retain their precedence.
            customizeDomains(builder);
            builder.addModules(modules);
            return builder.build();
        });
    }

    private void customizeDomains(JsonMapper.Builder builder) {
        GrailsDomainSerializers domainSerializers = new GrailsDomainSerializers(
                this::mappingContext, this::domainArtefact, this.proxyHandler,
                () -> booleanProperty("grails.converters.json.domain.include.version",
                        "grails.converters.domain.include.version"),
                () -> booleanProperty("grails.converters.json.domain.include.class",
                        "grails.converters.domain.include.class"));
        SimpleModule module = new SimpleModule("grails-domain-json") {
            @Override
            public void setupModule(SetupContext context) {
                super.setupModule(context);
                context.addSerializers(domainSerializers);
            }
        };
        builder.addModule(module);
    }
}
