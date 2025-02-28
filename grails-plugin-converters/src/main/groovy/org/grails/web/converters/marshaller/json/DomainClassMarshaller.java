/*
 * Copyright 2004-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.web.converters.marshaller.json;

import grails.converters.JSON;
import groovy.lang.GroovyObject;

import java.util.*;

import org.grails.core.artefact.DomainClassArtefactHandler;

import grails.core.GrailsApplication;
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
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.marshaller.ByDatasourceDomainClassFetcher;
import org.grails.web.converters.marshaller.ByGrailsApplicationDomainClassFetcher;
import org.grails.web.converters.marshaller.DomainClassFetcher;
import org.grails.web.converters.marshaller.IncludeExcludePropertyMarshaller;

import grails.core.support.proxy.DefaultProxyHandler;
import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;

import org.grails.web.json.JSONWriter;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;

/**
 *
 * Object marshaller for domain classes to JSON
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

    private List<DomainClassFetcher> domainClassFetchers;

    public DomainClassMarshaller(boolean includeVersion, GrailsApplication application) {
        this(includeVersion, new DefaultProxyHandler(), application);
        initializeDomainClassFetchers();
    }

    public DomainClassMarshaller(boolean includeVersion, ProxyHandler proxyHandler, GrailsApplication application) {
        this(includeVersion, false, proxyHandler, application);
        initializeDomainClassFetchers();
    }

    public DomainClassMarshaller(boolean includeVersion, boolean includeClass, ProxyHandler proxyHandler, GrailsApplication application) {
        this.includeVersion = includeVersion;
        this.includeClass = includeClass;
        this.proxyHandler = proxyHandler;
        this.application = application;
        initializeDomainClassFetchers();
    }

    private void initializeDomainClassFetchers() {
        this.domainClassFetchers = new ArrayList<DomainClassFetcher>() {{
            add(new ByGrailsApplicationDomainClassFetcher(application));
            add(new ByDatasourceDomainClassFetcher());
        }};
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
        return application.isArtefactOfType(DomainClassArtefactHandler.TYPE, name);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void marshalObject(Object value, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter();
        value = proxyHandler.unwrapIfProxy(value);
        Class<?> clazz = value.getClass();

        List<String> excludes = json.getExcludes(clazz);
        List<String> includes = json.getIncludes(clazz);
        IncludeExcludeSupport<String> includeExcludeSupport = new IncludeExcludeSupport<String>();


        BeanWrapper beanWrapper = new BeanWrapperImpl(value);

        writer.object();

        if(includeClass && shouldInclude(includeExcludeSupport, includes, excludes, value, "class")) {
            writer.key("class").value(clazz.getName());
        }

        PersistentEntity domainClass = findDomainClass(value);

        if ( domainClass == null ) {
            throw new GrailsConfigurationException("Could not retrieve the respective entity for domain " + value.getClass().getName() + " in the mapping context API");
        }

        PersistentProperty id = domainClass.getIdentity();
        if(id != null) {
            //Composite keys dont return an identity. They also do not render in the JSON. 
            //If using Composite keys, it may be advisable to use a customer Marshaller.
            if(shouldInclude(includeExcludeSupport, includes, excludes, value, id.getName())) {
                Object idValue = extractValue(value, id);
                if(idValue != null) {
                    json.property(id.getName(), idValue);
                }
            }    
        }
        

        if (shouldInclude(includeExcludeSupport, includes, excludes, value, GormProperties.VERSION) && isIncludeVersion()) {
            PersistentProperty versionProperty = domainClass.getVersion();
            Object version = extractValue(value, versionProperty);
            if(version != null) {
                json.property(GormProperties.VERSION, version);
            }
        }

        List<PersistentProperty> properties = domainClass.getPersistentProperties();

        for (PersistentProperty property : properties) {
            if (property.equals(domainClass.getVersion())) {
                continue;
            }

            if(!shouldInclude(includeExcludeSupport, includes, excludes, value, property.getName())) continue;

            writer.key(property.getName());
            if ( !(property instanceof Association) ) {
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
                        if (referencedDomainClass == null || ((Association)property).isEmbedded() || property.getType().isEnum()) {
                            json.convertAnother(referenceObject);
                        }
                        else if ( (property instanceof OneToOne) || (property instanceof ManyToOne)|| ((Association)property).isEmbedded()) {
                            asShortObject(referenceObject, json, referencedDomainClass.getIdentity(), referencedDomainClass);
                        }
                        else {
                            PersistentProperty referencedIdProperty = referencedDomainClass.getIdentity();
                            @SuppressWarnings("unused")
                            String refPropertyName = ((Association) property).getReferencedPropertyName();
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
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    String key = String.valueOf(entry.getKey());
                                    Object o = entry.getValue();
                                    writer.object();
                                    writer.key(key);
                                    asShortObject(o, json, referencedIdProperty, referencedDomainClass);
                                    writer.endObject();
                                }
                            }
                        }
                    }
                }
            }
        }
        writer.endObject();
    }

    private PersistentEntity findDomainClass(Object value) {
        for ( DomainClassFetcher fetcher : domainClassFetchers) {
            PersistentEntity domain = fetcher.findDomainClass(value);
            if ( domain != null ) {
                return domain;
            }
        }
        return null;
    }

    private boolean shouldInclude(IncludeExcludeSupport<String> includeExcludeSupport, List<String> includes, List<String> excludes, Object object, String propertyName) {
        return includeExcludeSupport.shouldInclude(includes,excludes,propertyName) && shouldInclude(object,propertyName);
    }

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
        if(isIncludeClass()) {
            writer.key("class").value(referencedDomainClass.getName());
        }
        if(idValue != null) {
            writer.key("id").value(idValue);
        }
        writer.endObject();
    }

    protected Object extractValue(Object domainObject, PersistentProperty property) {
        if(property == null) {
            return null;
        }
        if(domainObject instanceof GroovyObject) {
            return ((GroovyObject)domainObject).getProperty(property.getName());
        }
        else {
            ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(domainObject.getClass());
            return propertyFetcher.getPropertyValue(domainObject, property.getName());
        }
    }

    protected boolean isRenderDomainClassRelations() {
        return false;
    }
}
