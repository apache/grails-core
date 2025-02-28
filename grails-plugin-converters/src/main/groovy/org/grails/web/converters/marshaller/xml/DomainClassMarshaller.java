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
package org.grails.web.converters.marshaller.xml;

import grails.converters.XML;

import java.util.*;

import grails.core.GrailsApplication;

import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.core.util.IncludeExcludeSupport;

import grails.core.support.proxy.EntityProxyHandler;
import grails.core.support.proxy.ProxyHandler;

import org.grails.core.artefact.DomainClassArtefactHandler;
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
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.util.StringUtils;

/**
 *
 * Object marshaller for domain classes to XML
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 * @since 1.1
 */
public class DomainClassMarshaller extends IncludeExcludePropertyMarshaller<XML> {

    protected final boolean includeVersion;
    protected boolean includeClass = false;
    protected ProxyHandler proxyHandler;
    protected GrailsApplication application;
    private final List<DomainClassFetcher> domainClassFetchers;

    public DomainClassMarshaller(GrailsApplication application) {
        this(false, application);
    }

    public DomainClassMarshaller(boolean includeVersion, GrailsApplication application) {
        this.includeVersion = includeVersion;
        this.application = application;
        this.domainClassFetchers = Arrays.asList(
                new ByGrailsApplicationDomainClassFetcher(application),
                new ByDatasourceDomainClassFetcher()
        );
    }

    public DomainClassMarshaller(boolean includeVersion, ProxyHandler proxyHandler, GrailsApplication application) {
        this(includeVersion, application);
        this.proxyHandler = proxyHandler;
    }

    public DomainClassMarshaller(boolean includeVersion, boolean includeClass, ProxyHandler proxyHandler, GrailsApplication application) {
        this(includeVersion, proxyHandler, application);
        this.includeClass = includeClass;
    }

    public boolean supports(Object object) {
        String name = ConverterUtil.trimProxySuffix(object.getClass().getName());
        return application.isArtefactOfType(DomainClassArtefactHandler.TYPE, name);
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    public void marshalObject(Object value, XML xml) throws ConverterException {
        Class clazz = value.getClass();

        List<String> excludes = xml.getExcludes(clazz);
        List<String> includes = xml.getIncludes(clazz);
        IncludeExcludeSupport<String> includeExcludeSupport = new IncludeExcludeSupport<String>();

        PersistentEntity domainClass = findDomainClass(value);

        if ( domainClass == null ) {
            throw new GrailsConfigurationException("Could not retrieve the respective entity for domain " + value.getClass().getName() + " in the mapping context API");
        }
        BeanWrapper beanWrapper = new BeanWrapperImpl(value);

        PersistentProperty id = domainClass.getIdentity();
        if(shouldInclude(includeExcludeSupport, includes, excludes,value, id.getName())) {
            Object idValue = beanWrapper.getPropertyValue(id.getName());

            if (idValue != null) xml.attribute("id", String.valueOf(idValue));
        }

        if (shouldInclude(includeExcludeSupport, includes, excludes, value, GormProperties.VERSION) && includeVersion) {
            Object versionValue = beanWrapper.getPropertyValue(domainClass.getVersion().getName());
            if (versionValue != null) {
                final String str = String.valueOf(versionValue);
                if (StringUtils.hasText(str)) {
                    xml.attribute("version", str);
                }
            }
        }
        if(includeClass && shouldInclude(includeExcludeSupport, includes, excludes, value, "class")) {
            xml.attribute("class",domainClass.getJavaClass().getName());
        }

        List<PersistentProperty> properties = domainClass.getPersistentProperties();

        for (PersistentProperty property : properties) {
            String propertyName = property.getName();
            if (property.equals(domainClass.getVersion())) {
                continue;
            }

            if(!shouldInclude(includeExcludeSupport, includes, excludes, value, property.getName())) continue;

            xml.startNode(propertyName);
            if (!(property instanceof Association)) {
                // Write non-relation property
                Object val = beanWrapper.getPropertyValue(propertyName);
                xml.convertAnother(val);
            }
            else {
                if (isRenderDomainClassRelations()) {
                    Object referenceObject = beanWrapper.getPropertyValue(propertyName);
                    if (referenceObject != null && shouldInitializeProxy(referenceObject)) {
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
                        xml.convertAnother(referenceObject);
                    }
                }
                else {
                    Object referenceObject = beanWrapper.getPropertyValue(propertyName);
                    if (referenceObject != null) {
                        PersistentEntity referencedDomainClass = ((Association) property).getAssociatedEntity();

                        // Embedded are now always fully rendered
                        if (referencedDomainClass == null || ((Association)property).isEmbedded() || property.getType().isEnum()) {
                            xml.convertAnother(referenceObject);
                        }
                        else if ((property instanceof OneToOne) || (property instanceof ManyToOne)|| ((Association)property).isEmbedded()) {
                            asShortObject(referenceObject, xml, referencedDomainClass.getIdentity(), referencedDomainClass);
                        }
                        else {
                            PersistentProperty referencedIdProperty = referencedDomainClass.getIdentity();
                            @SuppressWarnings("unused")
                            String refPropertyName = ((Association) property).getReferencedPropertyName();
                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject;
                                for (Object el : o) {
                                    xml.startNode(xml.getElementName(el));
                                    asShortObject(el, xml, referencedIdProperty, referencedDomainClass);
                                    xml.end();
                                }
                            }
                            else if (referenceObject instanceof Map) {
                                Map<Object, Object> map = (Map<Object, Object>) referenceObject;
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    String key = String.valueOf(entry.getKey());
                                    Object o = entry.getValue();
                                    xml.startNode("entry").attribute("key", key);
                                    asShortObject(o, xml, referencedIdProperty, referencedDomainClass);
                                    xml.end();
                                }
                            }
                        }
                    }
                }
            }
            xml.end();
        }
    }

    private boolean shouldInclude(IncludeExcludeSupport<String> includeExcludeSupport, List<String> includes, List<String> excludes, Object object, String name) {
        return includeExcludeSupport.shouldInclude(includes, excludes, name) && shouldInclude(object,name);
    }

    private boolean shouldInitializeProxy(Object object) {
        return proxyHandler.isInitialized(object) || shouldInitializeProxies();
    }

    protected boolean shouldInitializeProxies() {
        return true;
    }


    protected void asShortObject(Object refObj, XML xml, PersistentProperty idProperty, PersistentEntity referencedDomainClass) throws ConverterException {
        Object idValue;
        if (proxyHandler instanceof EntityProxyHandler) {
            idValue = ((EntityProxyHandler) proxyHandler).getProxyIdentifier(refObj);
            if (idValue == null) {
                ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(refObj.getClass());
                idValue = propertyFetcher.getPropertyValue(refObj, idProperty.getName());
            }
        }
        else {
            ClassPropertyFetcher propertyFetcher = ClassPropertyFetcher.forClass(refObj.getClass());
            idValue = propertyFetcher.getPropertyValue(refObj, idProperty.getName());
        }
        xml.attribute(GormProperties.IDENTITY,String.valueOf(idValue));
    }

    protected boolean isRenderDomainClassRelations() {
        return false;
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

}
