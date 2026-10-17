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
import org.grails.web.converters.Converter.CircularReferenceBehaviour;
import org.grails.web.converters.configuration.ConvertersConfigurationInitializer;

/**
 * Adds Grails-specific serializers to Spring Boot's configured JSON mapper.
 *
 * @since 9.0
 */
public final class GrailsJsonMapperCustomizer implements JsonMapperBuilderCustomizer {

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

    private CircularReferenceBehaviour circularReferenceBehaviour() {
        return this.grailsApplication == null ? CircularReferenceBehaviour.DEFAULT :
                ConvertersConfigurationInitializer.jsonCircularReferenceBehaviour(this.grailsApplication.getConfig());
    }

    @Override
    public void customize(JsonMapper.Builder builder) {
        SimpleModule module = new SimpleModule("grails-json");
        module.addSerializer(GString.class, ToStringSerializer.instance);
        builder.addModule(module);
    }

    /**
     * Derives the mapper used by Grails responses without changing Spring MVC's mapper: the application's mapper, with
     * the rendering of domain classes, validation errors, projections and circular references of Grails responses.
     */
    public JsonMapper forGrails(JsonMapper mapper) {
        return grailsMappers.computeIfAbsent(mapper, source -> withGrailsModules(source,
                // The module the application's own mapper may have too, with this rendering: a response also honours the
                // circular reference behaviour of the JSON converter, which a plain Spring MVC response does not
                new DomainClassJacksonModule(() -> ConvertersConfigurationInitializer.jsonDomainClassRendering(
                        this.grailsApplication, this.proxyHandler)),
                // Do not change Errors returned from plain Spring MVC controllers.
                new SimpleModule("grails-domain-json").addSerializer(Errors.class, new SpringErrorsJsonSerializer(
                        () -> this.grailsApplication == null ? null : this.grailsApplication.getMainContext())),
                JsonProjection.module(),
                new CircularReferenceModule(this::circularReferenceBehaviour)).build());
    }

    /**
     * A copy of an application's mapper with Grails modules, which the JSON converter and Grails responses derive their
     * mappers with. The Grails modules are installed ahead of the application's, so that the application's serializers
     * keep their precedence over them, and a {@link DomainClassJacksonModule} of the application's mapper is replaced by
     * the one given.
     *
     * @param mapper the application's mapper
     * @param grailsModules the Grails modules
     * @return a builder of the copy
     */
    public static JsonMapper.Builder withGrailsModules(JsonMapper mapper, JacksonModule... grailsModules) {
        JsonMapper.Builder builder = mapper.rebuild();
        List<JacksonModule> modules = new ArrayList<>();
        builder.withModules(modules::add);
        builder.removeAllModules();
        builder.addModules(grailsModules);
        for (JacksonModule module : modules) {
            if (!(module instanceof DomainClassJacksonModule)) {
                builder.addModule(module);
            }
        }
        return builder;
    }
}
