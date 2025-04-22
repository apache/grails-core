/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.core;

import grails.core.GrailsDomainClass;
import grails.util.GrailsNameUtils;
import grails.validation.Constrained;
import org.grails.core.exceptions.GrailsConfigurationException;
import org.grails.core.io.support.GrailsFactoriesLoader;
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.validation.discovery.ConstrainedDiscovery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.ClassUtils;
import org.springframework.validation.Validator;

import java.util.*;

/**
 * Default implementation of the {@link GrailsDomainClass} interface
 *
 * @author Graeme Rocher
 * @since 0.1
 */
@SuppressWarnings("rawtypes")
@Deprecated
public class DefaultGrailsDomainClass extends AbstractGrailsClass implements GrailsDomainClass {

    private static final Logger log = LoggerFactory.getLogger(DefaultGrailsDomainClass.class);


    private PersistentEntity persistentEntity;
    private MappingContext mappingContext;
    private Map<String, Constrained> constrainedProperties;
    private Boolean autowire = null;

    public DefaultGrailsDomainClass(Class<?> clazz, MappingContext mappingContext) {
        this(clazz);
        this.mappingContext = mappingContext;
    }

    /**
     * Constructor.
     *
     * @param clazz
     */
    public DefaultGrailsDomainClass(Class<?> clazz) {
        super(clazz, "");
    }


    private void verifyContextIsInitialized() {
        if (mappingContext == null) {
            throw new GrailsConfigurationException("That API cannot be accessed before the spring context is initialized");
        } else {
            if (log.isWarnEnabled()) {
                log.warn("The GrailsDomainClass API should no longer be used to retrieve data about domain classes. Use the mapping context API instead");
            }
            if (persistentEntity == null) {
                persistentEntity = mappingContext.getPersistentEntity(this.getFullName());
                if (persistentEntity == null) {
                    MappingContext concreteMappingContext = getApplication().getMappingContext();
                    if(concreteMappingContext.getClass() == KeyValueMappingContext.class) {
                        // In a unit testing context, allow
                        persistentEntity = concreteMappingContext.addPersistentEntity(getClazz());
                    }
                    else {
                        throw new GrailsConfigurationException("Could not retrieve the respective entity for domain " + this.getName() + " in the mapping context API");
                    }
                }
            }
        }
    }

    @Override
    public boolean isAutowire() {
        if(autowire == null) {
            verifyContextIsInitialized();
            autowire = persistentEntity.getMapping().getMappedForm().isAutowire();
        }
        return autowire;
    }

    @Override
    public boolean isOwningClass(Class domainClass) {
        verifyContextIsInitialized();
        return persistentEntity.isOwningEntity(mappingContext.getPersistentEntity(domainClass.getName()));
    }

    /* (non-Javadoc)
     * @see org.grails.core.AbstractGrailsClass#getName()
     */
    @Override
    public String getName() {
        return ClassUtils.getShortName(super.getName());
    }

    /* (non-Javadoc)
     * @see grails.core.GrailsDomainClass#getPropertyName()
     */
    @Override
    public String getPropertyName() {
        return GrailsNameUtils.getPropertyNameRepresentation(getClazz());
    }

    /* (non-Javadoc)
     * @see grails.core.GrailsDomainClass#getConstraints()
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map getConstrainedProperties() {
        verifyContextIsInitialized();
        if(constrainedProperties == null) {
            ConstrainedDiscovery constrainedDiscovery = GrailsFactoriesLoader.loadFactory(ConstrainedDiscovery.class);
            if(constrainedDiscovery == null) {
                constrainedProperties = Collections.emptyMap();
            }
            else {
                constrainedProperties = constrainedDiscovery.findConstrainedProperties(persistentEntity);
            }
        }
        return constrainedProperties;
    }

    /* (non-Javadoc)
     * @see grails.core.GrailsDomainClass#getValidator()
     */
    public Validator getValidator() {
        verifyContextIsInitialized();
        return mappingContext.getEntityValidator(persistentEntity);
    }

    /* (non-Javadoc)
     * @see grails.core.GrailsDomainClass#setValidator(Validator validator)
     */
    public void setValidator(Validator validator) {
        verifyContextIsInitialized();
        mappingContext.addEntityValidator(persistentEntity, validator);
    }

}
