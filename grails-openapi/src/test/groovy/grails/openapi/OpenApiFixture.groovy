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
package grails.openapi

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.media.Schema
import org.springframework.context.support.GenericApplicationContext
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment

import grails.core.DefaultGrailsApplication
import grails.core.GrailsApplication
import grails.web.mapping.UrlMappingsHolder
import org.grails.datastore.gorm.validation.constraints.registry.DefaultValidatorRegistry
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.support.MockApplicationContext
import org.grails.web.mapping.DefaultUrlMappingEvaluator
import org.grails.web.mapping.DefaultUrlMappingsHolder

/**
 * Builds the generator the way the plugin does, from the application, its URL mappings, its
 * mapping contexts and its configuration.
 */
class OpenApiFixture {

    static GrailsOpenApiGenerator generator(UrlMappingsHolder holder, GrailsApplication application = null,
                                            MappingContext context = null, Map<String, Object> config = [:]) {
        new GrailsOpenApiGenerator(application, holder, context ? [context] : [], settings(config))
    }

    /**
     * The document an application with these controllers, entities, mappings and configuration
     * generates.
     */
    static OpenAPI document(Map<String, Object> config = [:], List<Class<?>> controllers, List<Class<?>> entities,
                            Closure mappings) {
        generator(holder(mappings), application(controllers), entities ? context(entities) : null, config).generate()
    }

    /**
     * The type a schema declares, which a 3.1 document holds as a set of types.
     */
    static String typeOf(Schema<?> schema) {
        schema.type ?: schema.types?.find()
    }

    static GrailsApplication application(List<Class<?>> controllers) {
        new DefaultGrailsApplication(controllers as Class[]).tap { it.initialise() }
    }

    /**
     * An application whose context holds the given controllers under their class names, as Grails
     * registers them.
     */
    static GrailsApplication application(List<Class<?>> controllers, List<Object> instances) {
        def ctx = new GenericApplicationContext()
        instances.each { ctx.beanFactory.registerSingleton(it.getClass().name, it) }
        ctx.refresh()
        application(controllers).tap { it.mainContext = ctx }
    }

    static UrlMappingsHolder holder(Closure mappings) {
        def ctx = new MockApplicationContext()
        ctx.registerMockBean(GrailsApplication.APPLICATION_ID, new DefaultGrailsApplication())
        new DefaultUrlMappingsHolder(new DefaultUrlMappingEvaluator(ctx).evaluateMappings(mappings))
    }

    static MappingContext context(List<Class<?>> entities) {
        MappingContext context = new KeyValueMappingContext('test')
        entities.each { context.addPersistentEntity(it) }
        // Constraints are only available once a validator registry has evaluated them.
        context.setValidatorRegistry(new DefaultValidatorRegistry(context, new ConnectionSourceSettings()))
        context
    }

    static OpenApiSettings settings(Map<String, Object> config) {
        def environment = new StandardEnvironment()
        environment.propertySources.addFirst(new MapPropertySource('test', config))
        OpenApiSettings.from(environment)
    }
}
