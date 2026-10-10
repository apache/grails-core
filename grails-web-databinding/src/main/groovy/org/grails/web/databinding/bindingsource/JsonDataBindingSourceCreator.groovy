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
package org.grails.web.databinding.bindingsource

import java.util.regex.Pattern

import groovy.transform.CompileStatic
import groovy.json.JsonException
import groovy.json.JsonSlurper
import groovy.util.logging.Slf4j

import jakarta.annotation.PostConstruct

import tools.jackson.core.JacksonException
import tools.jackson.databind.DeserializationFeature
import tools.jackson.databind.ObjectReader
import tools.jackson.databind.json.JsonMapper

import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext

import grails.databinding.CollectionDataBindingSource
import grails.databinding.DataBindingSource
import grails.databinding.SimpleMapDataBindingSource
import grails.web.mime.MimeType
import org.grails.databinding.bindingsource.DataBindingSourceCreationException
import org.grails.web.json.JSONObject

/**
 * Creates DataBindingSource objects from JSON in the request body
 *
 * @since 2.3
 * @author Jeff Brown
 * @author Graeme Rocher
 *
 * @see DataBindingSource
 * @see org.grails.databinding.bindingsource.DataBindingSourceCreator
 */
@CompileStatic
@Slf4j
class JsonDataBindingSourceCreator extends AbstractRequestBodyDataBindingSourceCreator {

    private static final Pattern INDEX_PATTERN = ~/^(\S+)\[(\d+)\]$/

    // Resolved when a request body is first parsed rather than injected. Injecting it pulls
    // Jackson's auto-configuration into this bean's graph, and MimeTypesConfiguration depends on
    // this creator, so Boot's mapper would be built before GORM has initialized.
    @Autowired(required = false)
    ObjectProvider<JsonMapper> jsonMapperProvider

    @Autowired(required = false)
    ApplicationContext applicationContext

    @Value('${grails.databinding.json.jackson:false}')
    boolean useJackson = false

    @PostConstruct
    void initialize() {
        if (!useJackson) {
            log.warn('Legacy JSON request parsing is deprecated for removal in Grails 11. ' +
                    'Set grails.databinding.json.jackson=true to opt into Jackson; it becomes the default in Grails 10.')
        }
    }

    private volatile JsonMapper resolvedJsonMapper

    JsonMapper getJsonMapper() {
        JsonMapper mapper = this.resolvedJsonMapper
        if (mapper == null) {
            mapper = jsonMapperProvider?.getIfUnique()
            if (mapper == null && applicationContext?.containsBean('jacksonJsonMapper')) {
                mapper = applicationContext.getBean('jacksonJsonMapper', JsonMapper)
            }
            mapper = mapper ?: JsonMapper.builder().build()
            this.resolvedJsonMapper = mapper
        }
        return mapper
    }

    void setJsonMapper(JsonMapper jsonMapper) {
        this.resolvedJsonMapper = jsonMapper
    }

    /**
     * Reads untyped JSON values. Decimals are read as {@link BigDecimal} so that binding a
     * fractional value to a BigDecimal property keeps the digits the request sent; reading them
     * as doubles first would round them before the binder ever saw them.
     */
    protected ObjectReader untypedReader() {
        return getJsonMapper().reader().forType(Object).with(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
    }

    @Override
    MimeType[] getMimeTypes() {
        [MimeType.JSON, MimeType.TEXT_JSON] as MimeType[]
    }

    @Override
    DataBindingSource createDataBindingSource(MimeType mimeType, Class bindingTargetType, Object bindingSource) {
        if (bindingSource instanceof Map) {
            return new SimpleMapDataBindingSource(createJsonMap(bindingSource))
        }
        else if (bindingSource instanceof JSONObject) {
            return new SimpleMapDataBindingSource((JSONObject) bindingSource)
        }
        else {
            return super.createDataBindingSource(mimeType, bindingTargetType, bindingSource)
        }
    }

    @Override
    protected CollectionDataBindingSource createCollectionBindingSource(Reader reader) {

        Object jsonElement = parse(reader)
        def dataBindingSources = jsonElement.collect { element ->
            if (element instanceof Map) {
                new SimpleMapDataBindingSource(createJsonMap(element))
            }
            else {
                new SimpleMapDataBindingSource(Collections.emptyMap())
            }
        }
        return new CollectionDataBindingSource() {
            List<DataBindingSource> getDataBindingSources() {
                (List<DataBindingSource>) dataBindingSources
            }
        }
    }

    @Override
    protected DataBindingSource createBindingSource(Reader reader) {
        final Object jsonElement = parse(reader)

        if (jsonElement instanceof Map) {
            return new SimpleMapDataBindingSource(createJsonMap(jsonElement))
        }
        else {
            return new SimpleMapDataBindingSource(Collections.emptyMap())
        }

    }

    protected Map createJsonMap(Object jsonElement) {
        (Map) jsonElement
    }

    private Object parse(Reader reader) {
        return useJackson ? untypedReader().readValue(reader) : new JsonSlurper().parse(reader)
    }

    @Override
    protected DataBindingSourceCreationException createBindingSourceCreationException(Exception e) {
        if (e instanceof JacksonException || e instanceof JsonException) {
            return new InvalidRequestBodyException(e)
        }
        return super.createBindingSourceCreationException(e)
    }
}
