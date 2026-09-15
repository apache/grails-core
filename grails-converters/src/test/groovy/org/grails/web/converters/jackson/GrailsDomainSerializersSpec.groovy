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
package org.grails.web.converters.jackson

import java.util.function.Predicate
import java.util.function.Supplier

import tools.jackson.databind.JavaType
import tools.jackson.databind.type.TypeFactory

import grails.core.support.proxy.DefaultProxyHandler
import org.grails.core.exceptions.GrailsConfigurationException
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

import spock.lang.Specification

/**
 * GrailsApplication hands out a mapping context proxy that only fails when a method is called, so
 * "GORM is not ready" is signalled by an exception from a lookup, never by a null context.
 */
class GrailsDomainSerializersSpec extends Specification {

    private static final JavaType BOOK = TypeFactory.createDefaultInstance().constructType(JacksonBook)

    void 'a domain class asked for before GORM is ready gets a serializer that binds later'() {
        given: "a context that throws on lookup, as the proxy does before GORM initializes"
        def serializers = serializers({ throwingContext() }, { true })

        expect: "a serializer of ours is returned, so Jackson does not cache a bean serializer"
        serializers.findSerializer(null, BOOK, null, null) != null
    }

    void 'a class that is not a domain artefact is left to Jackson'() {
        given:
        def serializers = serializers({ throwingContext() }, { false })

        expect: "returning null lets Jackson choose, which is correct for an unmapped type"
        serializers.findSerializer(null, BOOK, null, null) == null
    }

    void 'an unmapped class is left to Jackson once GORM is ready'() {
        given: "a readable context that simply does not map this class"
        def serializers = serializers({ new KeyValueMappingContext('test') }, { true })

        expect:
        serializers.findSerializer(null, BOOK, null, null) == null
    }

    void 'a mapped class resolves to the domain serializer'() {
        given:
        def context = new KeyValueMappingContext('test')
        context.addPersistentEntities(JacksonBook)
        def serializers = serializers({ context }, { true })

        expect:
        serializers.findSerializer(null, BOOK, null, null) instanceof GrailsDomainJsonSerializer
    }

    private MappingContext throwingContext() {
        // Mirrors the proxy DefaultGrailsApplication returns: non-null, but any lookup fails.
        return new KeyValueMappingContext('test') {
            @Override
            PersistentEntity getPersistentEntity(String name) {
                throw new GrailsConfigurationException('not initialized')
            }
        }
    }

    private GrailsDomainSerializers serializers(Supplier<MappingContext> context,
            Predicate<Class<?>> domainArtefact) {
        return new GrailsDomainSerializers(context, domainArtefact, new DefaultProxyHandler(),
                { false } as Supplier<Boolean>, { false } as Supplier<Boolean>)
    }
}
