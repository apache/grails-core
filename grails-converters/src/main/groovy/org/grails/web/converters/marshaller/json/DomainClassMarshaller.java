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
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

import groovy.lang.GroovyObject;

import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.ReflectionUtils;

import grails.converters.JSON;
import grails.core.GrailsApplication;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.core.util.IncludeExcludeSupport;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.ManyToOne;
import org.grails.datastore.mapping.model.types.OneToOne;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.web.converters.ConverterUtil;
import org.grails.web.converters.configuration.ConvertersConfigurationHolder;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.jackson.DomainClassRendering;
import org.grails.web.converters.jackson.DomainClassSerializer;
import org.grails.web.converters.marshaller.ByDatasourceDomainClassFetcher;
import org.grails.web.converters.marshaller.ByGrailsApplicationDomainClassFetcher;
import org.grails.web.converters.marshaller.DomainClassFetcher;
import org.grails.web.converters.marshaller.IncludeExcludePropertyMarshaller;
import org.grails.web.json.JSONWriter;

/**
 *
 * Object marshaller for domain classes to JSON. It renders an instance with a
 * {@link DomainClassSerializer}, through the converter's {@code JsonMapper}, with the includes and excludes of the
 * converter and of {@link #includesProperty(Object, String)} and {@link #excludesProperty(Object, String)}, and renders
 * the property values with the converter. With {@code grails.converters.json.legacy}, it renders an instance as
 * Grails 8 did.
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

    private List<DomainClassFetcher> domainClassFetchers;

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
        if (ConvertersConfigurationHolder.isLegacyJson()) {
            marshalObjectAsGrails8(value, json);
            return;
        }
        Object object = proxyHandler.unwrapIfProxy(value);
        JsonMapperValueMarshaller.write(DomainClassSerializer.value(object, new Rendering(json, object.getClass())), json);
    }

    /**
     * Renders an instance as Grails 8 did, except that a to-many association whose value is a {@code Map} is an object
     * of all its entries, where Grails 8 failed unless it had exactly one.
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void marshalObjectAsGrails8(Object value, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter();
        value = proxyHandler.unwrapIfProxy(value);
        Class<?> clazz = value.getClass();

        List<String> excludes = json.getExcludes(clazz);
        List<String> includes = json.getIncludes(clazz);
        IncludeExcludeSupport<String> includeExcludeSupport = new IncludeExcludeSupport<>();

        BeanWrapper beanWrapper = new BeanWrapperImpl(value);

        writer.object();

        if (includeClass && shouldInclude(includeExcludeSupport, includes, excludes, value, "class")) {
            writer.key("class").value(clazz.getName());
        }

        PersistentEntity domainClass = findDomainClass(value);

        if (domainClass == null) {
            throw new GrailsConfigurationException("Could not retrieve the respective entity for domain " + value.getClass().getName() + " in the mapping context API");
        }

        PersistentProperty id = domainClass.getIdentity();
        if (id != null) {
            //Composite keys dont return an identity. They also do not render in the JSON.
            //If using Composite keys, it may be advisable to use a customer Marshaller.
            if (shouldInclude(includeExcludeSupport, includes, excludes, value, id.getName())) {
                Object idValue = extractValue(value, id);
                if (idValue != null) {
                    json.property(id.getName(), idValue);
                }
            }
        }

        if (shouldInclude(includeExcludeSupport, includes, excludes, value, GormProperties.VERSION) && isIncludeVersion()) {
            PersistentProperty versionProperty = domainClass.getVersion();
            Object version = extractValue(value, versionProperty);
            if (version != null) {
                json.property(GormProperties.VERSION, version);
            }
        }

        List<PersistentProperty> properties = domainClass.getPersistentProperties();

        for (PersistentProperty property : properties) {
            if (property.equals(domainClass.getVersion())) {
                continue;
            }

            if (!shouldInclude(includeExcludeSupport, includes, excludes, value, property.getName())) continue;

            writer.key(property.getName());
            if (!(property instanceof Association)) {
                // Write non-relation property
                Object val = beanWrapper.getPropertyValue(property.getName());
                json.convertAnother(val);
            }
            else {
                Object referenceObject = beanWrapper.getPropertyValue(property.getName());
                if (isRenderDomainClassRelations()) {
                    if (referenceObject == null) {
                        writer.valueNull();
                    }
                    else {
                        referenceObject = proxyHandler.unwrapIfProxy(referenceObject);
                        if (referenceObject instanceof SortedMap) {
                            referenceObject = new TreeMap((SortedMap) referenceObject);
                        }
                        else if (referenceObject instanceof SortedSet) {
                            referenceObject = new TreeSet((SortedSet) referenceObject);
                        }
                        else if (referenceObject instanceof Set) {
                            referenceObject = new LinkedHashSet((Set) referenceObject);
                        }
                        else if (referenceObject instanceof Map) {
                            referenceObject = new LinkedHashMap((Map) referenceObject);
                        }
                        else if (referenceObject instanceof Collection) {
                            referenceObject = new ArrayList((Collection) referenceObject);
                        }
                        json.convertAnother(referenceObject);
                    }
                }
                else {
                    if (referenceObject == null) {
                        json.value(null);
                    }
                    else {

                        PersistentEntity referencedDomainClass = ((Association) property).getAssociatedEntity();

                        // Embedded are now always fully rendered
                        if (referencedDomainClass == null || ((Association) property).isEmbedded() || property.getType().isEnum()) {
                            json.convertAnother(referenceObject);
                        }
                        else if ((property instanceof OneToOne) || (property instanceof ManyToOne) || ((Association) property).isEmbedded()) {
                            asShortObject(referenceObject, json, referencedDomainClass.getIdentity(), referencedDomainClass);
                        }
                        else {
                            PersistentProperty referencedIdProperty = referencedDomainClass.getIdentity();
                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject;
                                writer.array();
                                for (Object el : o) {
                                    asShortObject(el, json, referencedIdProperty, referencedDomainClass);
                                }
                                writer.endArray();
                            }
                            else if (referenceObject instanceof Map) {
                                Map<Object, Object> map = (Map<Object, Object>) referenceObject;
                                writer.object();
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    writer.key(String.valueOf(entry.getKey()));
                                    asShortObject(entry.getValue(), json, referencedIdProperty, referencedDomainClass);
                                }
                                writer.endObject();
                            }
                        }
                    }
                }
            }
        }
        writer.endObject();
    }

    private PersistentEntity findDomainClass(Object value) {
        if (domainClassFetchers == null) {
            domainClassFetchers = List.of(new ByGrailsApplicationDomainClassFetcher(application), new ByDatasourceDomainClassFetcher());
        }
        for (DomainClassFetcher fetcher : domainClassFetchers) {
            PersistentEntity domain = fetcher.findDomainClass(value);
            if (domain != null) {
                return domain;
            }
        }
        return null;
    }

    private boolean shouldInclude(IncludeExcludeSupport<String> includeExcludeSupport, List<String> includes, List<String> excludes, Object object, String propertyName) {
        return includeExcludeSupport.shouldInclude(includes, excludes, propertyName) && shouldInclude(object, propertyName);
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
