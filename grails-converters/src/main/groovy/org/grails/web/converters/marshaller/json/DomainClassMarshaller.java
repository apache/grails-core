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
package org.grails.web.converters.marshaller.json;

import java.lang.reflect.Method;
import java.util.List;

import groovy.lang.GroovyObject;

import org.springframework.util.ReflectionUtils;

import grails.converters.JSON;
import grails.core.GrailsApplication;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.core.util.IncludeExcludeSupport;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.web.converters.ConverterUtil;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.jackson.DomainClassRendering;
import org.grails.web.converters.jackson.DomainClassSerializer;
import org.grails.web.converters.marshaller.IncludeExcludePropertyMarshaller;
import org.grails.web.json.JSONWriter;

/**
 *
 * Object marshaller for domain classes to JSON. It renders an instance with a
 * {@link DomainClassSerializer}, through the converter's {@code JsonMapper}, with the includes and excludes of the
 * converter and of {@link #includesProperty(Object, String)} and {@link #excludesProperty(Object, String)}, and renders
 * the property values with the converter.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 *
 * @since 1.1
 */
public class DomainClassMarshaller extends IncludeExcludePropertyMarshaller<JSON> {

    private boolean includeVersion = false;
    private boolean includeClass = false;
    private ProxyHandler proxyHandler;
    private GrailsApplication application;

    private final boolean overridesAsShortObject;

    public DomainClassMarshaller(boolean includeVersion, GrailsApplication application) {
        this(includeVersion, new DefaultProxyHandler(), application);
    }

    public DomainClassMarshaller(boolean includeVersion, ProxyHandler proxyHandler, GrailsApplication application) {
        this(includeVersion, false, proxyHandler, application);
    }

    public DomainClassMarshaller(boolean includeVersion, boolean includeClass, ProxyHandler proxyHandler, GrailsApplication application) {
        this.includeVersion = includeVersion;
        this.includeClass = includeClass;
        this.proxyHandler = proxyHandler;
        this.application = application;
        Method asShortObject = ReflectionUtils.findMethod(getClass(), "asShortObject", Object.class, JSON.class,
                PersistentProperty.class, PersistentEntity.class);
        this.overridesAsShortObject = asShortObject != null && asShortObject.getDeclaringClass() != DomainClassMarshaller.class;
    }

    public boolean isIncludeVersion() {
        return includeVersion;
    }

    public boolean isIncludeClass() {
        return includeClass;
    }

    public void setIncludeClass(boolean includeClass) {
        this.includeClass = includeClass;
    }

    public void setIncludeVersion(boolean includeVersion) {
        this.includeVersion = includeVersion;
    }

    public boolean supports(Object object) {
        String name = ConverterUtil.trimProxySuffix(object.getClass().getName());
        return application != null && application.isArtefactOfType(DomainClassArtefactHandler.TYPE, name);
    }

    public void marshalObject(Object value, JSON json) throws ConverterException {
        Object object = proxyHandler.unwrapIfProxy(value);
        JsonMapperValueMarshaller.write(DomainClassSerializer.value(object, new Rendering(json, object.getClass())), json);
    }

    /**
     * Writes a reference to an associated domain class instance: {@code {"id": …}}, with the class name when it is
     * included. A subclass that overrides it writes the references it renders.
     */
    protected void asShortObject(Object refObj, JSON json, PersistentProperty idProperty, PersistentEntity referencedDomainClass) throws ConverterException {

        Object idValue;

        if (proxyHandler instanceof EntityProxyHandler) {
            idValue = ((EntityProxyHandler) proxyHandler).getProxyIdentifier(refObj);
            if (idValue == null) {
                idValue = extractValue(refObj, idProperty);
            }
        }
        else {
            idValue = extractValue(refObj, idProperty);
        }
        JSONWriter writer = json.getWriter();
        writer.object();
        if (isIncludeClass()) {
            writer.key("class").value(referencedDomainClass.getName());
        }
        if (idValue != null) {
            writer.key("id").value(idValue);
        }
        writer.endObject();
    }

    protected Object extractValue(Object domainObject, PersistentProperty property) {
        if (property == null) {
            return null;
        }
        if (domainObject instanceof GroovyObject) {
            return ((GroovyObject) domainObject).getProperty(property.getName());
        }
        else {
            ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(domainObject.getClass());
            return propertyFetcher.getPropertyValue(domainObject, property.getName());
        }
    }

    protected boolean isRenderDomainClassRelations() {
        return false;
    }

    /**
     * Renders an instance as this marshaller is configured to, with the includes and excludes of the converter.
     */
    private final class Rendering extends DomainClassRendering {

        private final JSON json;

        private final List<String> includes;

        private final List<String> excludes;

        private final IncludeExcludeSupport<String> includeExcludeSupport = new IncludeExcludeSupport<>();

        private Rendering(JSON json, Class<?> type) {
            super(application, proxyHandler, DomainClassMarshaller.this.isIncludeVersion(),
                    DomainClassMarshaller.this.isIncludeClass(), isRenderDomainClassRelations());
            this.json = json;
            this.includes = json.getIncludes(type);
            this.excludes = json.getExcludes(type);
        }

        @Override
        public boolean includes(Object object, String property) {
            return includeExcludeSupport.shouldInclude(includes, excludes, property) && shouldInclude(object, property);
        }

        @Override
        public Object propertyValue(Object object, PersistentProperty property) {
            return extractValue(object, property);
        }

        @Override
        public boolean writeReference(Object reference, PersistentProperty idProperty, PersistentEntity entity) {
            if (!overridesAsShortObject) {
                return false;
            }
            json.getWriter().writeNested(() -> asShortObject(reference, json, idProperty, entity));
            return true;
        }
    }
}
