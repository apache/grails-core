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

package org.grails.datastore.mapping.config;

import groovy.lang.Closure;

import java.beans.PropertyDescriptor;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.grails.datastore.mapping.config.groovy.DefaultMappingConfigurationBuilder;
import org.grails.datastore.mapping.config.groovy.MappingConfigurationBuilder;
import org.grails.datastore.mapping.keyvalue.mapping.config.Family;
import org.grails.datastore.mapping.model.*;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher;
import org.springframework.beans.BeanUtils;

/**
 * Abstract GORM implementation that uses the GORM MappingConfigurationBuilder to configure entity mappings.
 *
 * @author Graeme Rocher
 */
@SuppressWarnings({"rawtypes", "unchecked"})
public abstract class AbstractGormMappingFactory<R extends Entity, T extends Property> extends MappingFactory<R, T> {

    protected Map<PersistentEntity, Map<String, T>> entityToPropertyMap = new HashMap<PersistentEntity, Map<String, T>>();
    protected Map<PersistentEntity, R> entityToMapping = new HashMap<PersistentEntity, R>();
    private Closure defaultMapping;
    private Object contextObject;
    protected Closure defaultConstraints;
    protected boolean versionByDefault = true;

    /**
     * @param contextObject Context object to be passed to mapping blocks
     */
    public void setContextObject(Object contextObject) {
        this.contextObject = contextObject;
    }

    public void setDefaultConstraints(Closure defaultConstraints) {
        this.defaultConstraints = defaultConstraints;
    }

    /**
     * Whether the version entities using optimistic locking by default
     *
     * @param versionByDefault True if objects should be versioned by default
     */
    public void setVersionByDefault(boolean versionByDefault) {
        this.versionByDefault = versionByDefault;
    }

    @Override
    public R createMappedForm(PersistentEntity entity) {
        if(entityToMapping.containsKey(entity)) {
            return entityToMapping.get(entity);
        }
        else {
            R family = BeanUtils.instantiateClass(getEntityMappedFormType());
            entityToMapping.put(entity, family);
            MappingConfigurationBuilder builder = createConfigurationBuilder(entity, family);

            if (defaultMapping != null) {
                evaluateWithContext(builder, defaultMapping);
            }
            if (defaultConstraints != null) {
                evaluateWithContext(builder, defaultConstraints);
            }
            List<Object> values = ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy(entity.getJavaClass(),GormProperties.MAPPING, Object.class);
            for (Object value : values) {
                if(value instanceof MappingDefinition && !(family instanceof Family)) {
                    MappingDefinition definition = (MappingDefinition) value;
                    definition.configure(family);
                }
                else if(value instanceof Closure) {
                    evaluateWithContext(builder, (Closure) value);
                }
            }
            List<Closure> constraintValues = ClassPropertyFetcher.getStaticPropertyValuesFromInheritanceHierarchy(entity.getJavaClass(),GormProperties.CONSTRAINTS, Closure.class);
            for (Closure value : constraintValues) {
                evaluateWithContext(builder, value);
            }
            Map properties = builder.getProperties();


            entityToPropertyMap.put(entity, properties);
            return family;
        }
    }

    protected void evaluateWithContext(MappingConfigurationBuilder builder, Closure value) {
        if(contextObject != null) {
            builder.evaluate(value, contextObject);
        }
        else {
            builder.evaluate(value);
        }
    }

    protected MappingConfigurationBuilder createConfigurationBuilder(PersistentEntity entity, R family) {
        family.setVersion(versionByDefault);
        return new DefaultMappingConfigurationBuilder(family, getPropertyMappedFormType());
    }

    public void setDefaultMapping(Closure defaultMapping) {
        this.defaultMapping = defaultMapping;
    }

    protected abstract Class<T> getPropertyMappedFormType();

    protected abstract Class<R> getEntityMappedFormType();

    @Override
    public boolean isTenantId(PersistentEntity entity, MappingContext context, PropertyDescriptor descriptor) {
        if(entity.isMultiTenant()) {
            Map<String, T> props = entityToPropertyMap.get(entity);
            if(props != null && props.containsKey(GormProperties.TENANT_IDENTITY)) {
                T tenantIdProp = props.get(GormProperties.TENANT_IDENTITY);
                String propertyName = tenantIdProp.getName();
                if(descriptor.getName().equals(propertyName)) {
                    return true;
                }
            }
            else {
                return descriptor.getName().equals(GormProperties.TENANT_IDENTITY);
            }
        }
        return false;
    }

    @Override
    public IdentityMapping createIdentityMapping(ClassMapping classMapping) {
        Map<String, T> props = entityToPropertyMap.get(classMapping.getEntity());
        if (props != null) {
            T property  = props.get(IDENTITY_PROPERTY);
            IdentityMapping customIdentityMapping = getIdentityMappedForm(classMapping,property);
            if (customIdentityMapping != null) {
                return customIdentityMapping;
            }
        }
        return super.createIdentityMapping(classMapping);
    }

    protected IdentityMapping getIdentityMappedForm(final ClassMapping classMapping, T property) {
        if(property != null) {
            return createDefaultIdentityMapping(classMapping, property);
        }
        else {
            return createDefaultIdentityMapping(classMapping);
        }
    }

    @Override
    public T createMappedForm(PersistentProperty mpp) {
        Map<String, T> properties = entityToPropertyMap.get(mpp.getOwner());
        if (properties != null && properties.containsKey(mpp.getName())) {
            return properties.get(mpp.getName());
        }
        else if (properties != null) {
            Property property  = properties.get(IDENTITY_PROPERTY);
            if (property != null && mpp.getName().equals(property.getName())) {
                return (T) property;
            }
        }

        T defaultMapping = properties != null ? properties.get("*") : null;
        if (defaultMapping != null) {
            try {
                return (T) defaultMapping.clone();
            } catch (CloneNotSupportedException e) {
                return BeanUtils.instantiateClass(getPropertyMappedFormType());
            }
        }
        else {
            return BeanUtils.instantiateClass(getPropertyMappedFormType());
        }
    }
}
