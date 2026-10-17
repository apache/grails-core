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
package org.grails.web.converters.jackson;

import java.util.List;

import groovy.lang.GroovyObject;

import org.springframework.util.ClassUtils;

import grails.core.GrailsApplication;
import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;
import org.grails.core.artefact.DomainClassArtefactHandler;
import org.grails.datastore.gorm.GormEnhancer;
import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PersistentProperty;
import org.grails.datastore.mapping.proxy.EntityProxy;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.grails.web.converters.Converter.CircularReferenceBehaviour;
import org.grails.web.converters.ConverterUtil;
import org.grails.web.converters.marshaller.ByDatasourceDomainClassFetcher;
import org.grails.web.converters.marshaller.ByGrailsApplicationDomainClassFetcher;
import org.grails.web.converters.marshaller.DomainClassFetcher;

/**
 * How a {@link DomainClassJacksonModule} renders domain class instances: whether it writes the version and the class
 * name, whether it renders associated domain class instances in full (deep) or as references of their id, and which
 * properties it writes.
 *
 * <p>The defaults are the converter's JSON defaults ({@code grails.converters.json.domain.include.version},
 * {@code grails.converters.json.domain.include.class} and {@code grails.converters.json.default.deep}), and every
 * persistent property is written except those Jackson leaves out, such as a property annotated {@code @JsonIgnore} or
 * named by {@code @JsonIgnoreProperties}. GORM adds that annotation to every domain class, naming properties such as
 * {@code errors} and {@code tenantId}; the version is written when it is included regardless. Subclasses override the
 * methods to render differently.
 *
 * @since 9.0
 */
public class DomainClassRendering {

    private static final String HIBERNATE_PROXY = "org.hibernate.proxy.HibernateProxy";

    private final GrailsApplication grailsApplication;

    private final ProxyHandler proxyHandler;

    private final boolean includeVersion;

    private final boolean includeClass;

    private final boolean deep;

    private final CircularReferenceBehaviour circularReferenceBehaviour;

    private final List<DomainClassFetcher> domainClassFetchers;

    /**
     * @param grailsApplication the application whose domain classes are rendered
     * @param proxyHandler unwraps proxied domain class instances
     * @param includeVersion whether to write the version
     * @param includeClass whether to write the class name
     * @param deep whether to render associated domain class instances in full, rather than as references of their id
     */
    public DomainClassRendering(GrailsApplication grailsApplication, ProxyHandler proxyHandler, boolean includeVersion,
            boolean includeClass, boolean deep) {
        this(grailsApplication, proxyHandler, includeVersion, includeClass, deep, null);
    }

    /**
     * @param grailsApplication the application whose domain classes are rendered
     * @param proxyHandler unwraps proxied domain class instances
     * @param includeVersion whether to write the version
     * @param includeClass whether to write the class name
     * @param deep whether to render associated domain class instances in full, rather than as references of their id
     * @param circularReferenceBehaviour what to write for an instance that is reached again while it is being written,
     * as {@code grails.converters.json.circular.reference.behaviour} selects for the JSON converter; {@code null}
     * writes a reference of its id
     */
    public DomainClassRendering(GrailsApplication grailsApplication, ProxyHandler proxyHandler, boolean includeVersion,
            boolean includeClass, boolean deep, CircularReferenceBehaviour circularReferenceBehaviour) {
        this.circularReferenceBehaviour = circularReferenceBehaviour;
        this.grailsApplication = grailsApplication;
        this.proxyHandler = proxyHandler != null ? proxyHandler : new DefaultProxyHandler();
        this.includeVersion = includeVersion;
        this.includeClass = includeClass;
        this.deep = deep;
        this.domainClassFetchers = grailsApplication != null ?
                List.of(new ByGrailsApplicationDomainClassFetcher(grailsApplication), new ByDatasourceDomainClassFetcher()) :
                List.of(new ByDatasourceDomainClassFetcher());
    }

    /**
     * @return whether the version is written
     */
    public boolean isIncludeVersion() {
        return includeVersion;
    }

    /**
     * @return whether the class name is written, of an instance and of the references to associated instances
     */
    public boolean isIncludeClass() {
        return includeClass;
    }

    /**
     * @return whether associated domain class instances are rendered in full, rather than as references of their id
     */
    public boolean isDeep() {
        return deep;
    }

    /**
     * @return what is written for an instance reached again while it is being written, or {@code null} for a reference
     * of its id
     */
    public CircularReferenceBehaviour getCircularReferenceBehaviour() {
        return circularReferenceBehaviour;
    }

    /**
     * @param object a domain class instance
     * @param property the name of one of its properties, or {@code class} or {@code version}
     * @return whether the property is written
     */
    public boolean includes(Object object, String property) {
        return true;
    }

    /**
     * @param type a type
     * @return whether the type is a domain class, or a proxy class of one; another subclass of a domain class is not
     */
    public boolean isDomainClass(Class<?> type) {
        if (grailsApplication == null) {
            return false;
        }
        if (isArtefact(type)) {
            return true;
        }
        if (!isProxyClass(type)) {
            return false;
        }
        for (Class<?> current = type.getSuperclass(); current != null && current != Object.class; current = current.getSuperclass()) {
            if (isArtefact(current)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether the rendering knows the application's domain classes
     */
    boolean hasApplication() {
        return grailsApplication != null;
    }

    private boolean isArtefact(Class<?> type) {
        return grailsApplication.isArtefactOfType(DomainClassArtefactHandler.TYPE, ConverterUtil.trimProxySuffix(type.getName()));
    }

    /**
     * Whether the type is a GORM or Hibernate proxy class, which subclasses the class it proxies.
     */
    static boolean isProxyClass(Class<?> type) {
        if (EntityProxy.class.isAssignableFrom(type)) {
            return true;
        }
        for (Class<?> implemented : ClassUtils.getAllInterfacesForClassAsSet(type)) {
            if (HIBERNATE_PROXY.equals(implemented.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * A GORM proxy is unwrapped by itself when the proxy handler leaves it as it is: only Hibernate registers a proxy
     * handler, so that of an application on another datastore, such as MongoDB, unwraps nothing.
     *
     * @param value a domain class instance, or a proxy of one
     * @return the instance itself
     */
    public Object unwrap(Object value) {
        Object unwrapped = proxyHandler.unwrapIfProxy(value);
        if (unwrapped == value && value instanceof EntityProxy<?> proxy) {
            return proxy.getTarget();
        }
        return unwrapped;
    }

    /**
     * @param object a domain class instance
     * @return its persistent entity, or {@code null} if it has none
     */
    public PersistentEntity findEntity(Object object) {
        for (DomainClassFetcher fetcher : domainClassFetchers) {
            PersistentEntity entity = fetcher.findDomainClass(object);
            if (entity != null) {
                return entity;
            }
        }
        return null;
    }

    /**
     * @param type a domain class, which is not a proxy class
     * @return its persistent entity, or {@code null} if it has none
     * @throws org.grails.core.exceptions.GrailsConfigurationException if GORM has not yet made its mapping metadata
     * readable
     */
    PersistentEntity findEntityOfType(Class<?> type) {
        PersistentEntity entity = grailsApplication != null ?
                grailsApplication.getMappingContext().getPersistentEntity(type.getName()) : null;
        if (entity == null) {
            Datastore datastore = GormEnhancer.findDatastore(type);
            MappingContext mappingContext = datastore != null ? datastore.getMappingContext() : null;
            entity = mappingContext != null ? mappingContext.getPersistentEntity(type.getName()) : null;
        }
        return entity;
    }

    /**
     * The value of the id or version property of a domain class instance.
     *
     * @param object a domain class instance
     * @param property its id or version property, or {@code null}
     * @return the value, or {@code null} for a {@code null} property
     */
    public Object propertyValue(Object object, PersistentProperty property) {
        if (property == null) {
            return null;
        }
        if (object instanceof GroovyObject groovyObject) {
            return groovyObject.getProperty(property.getName());
        }
        return ClassPropertyFetcher.forClass(object.getClass()).getPropertyValue(object, property.getName());
    }

    /**
     * The id of an associated domain class instance, without initializing it if it is a proxy.
     *
     * @param reference an associated domain class instance, or a proxy of one
     * @param idProperty the id property of its domain class
     * @return the id, or {@code null}
     */
    public Object referenceId(Object reference, PersistentProperty idProperty) {
        if (proxyHandler instanceof EntityProxyHandler entityProxyHandler) {
            Object id = entityProxyHandler.getProxyIdentifier(reference);
            if (id != null) {
                return id;
            }
        }
        if (reference instanceof EntityProxy<?> proxy && proxy.getProxyKey() != null) {
            return proxy.getProxyKey();
        }
        return propertyValue(reference, idProperty);
    }

    /**
     * Writes the reference to an associated domain class instance itself, in place of the
     * {@code {"class": …, "id": …}} object the serializer writes.
     *
     * @param reference an associated domain class instance
     * @param idProperty the id property of its domain class
     * @param entity its domain class
     * @return whether the reference was written
     */
    public boolean writeReference(Object reference, PersistentProperty idProperty, PersistentEntity entity) {
        return false;
    }
}
