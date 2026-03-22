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
package org.grails.orm.hibernate.cfg;

import java.util.List;

import groovy.lang.Closure;

import grails.gorm.hibernate.HibernateEntity;
import org.grails.datastore.gorm.GormEntity;
import org.grails.datastore.mapping.model.AbstractMappingContext;
import org.grails.datastore.mapping.model.MappingConfigurationStrategy;
import org.grails.datastore.mapping.model.MappingFactory;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsJpaMappingConfigurationStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEmbeddedPersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateMappingFactory;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.proxy.HibernateProxyHandler;

/**
 * A Mapping context for Hibernate optimized for Java to Groovy conversion.
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernateMappingContext extends AbstractMappingContext {

    private final HibernateMappingFactory mappingFactory;
    private final MappingConfigurationStrategy syntaxStrategy;
    private final MappingCacheHolder mappingCacheHolder = new MappingCacheHolder();

    public MappingCacheHolder getMappingCacheHolder() {
        return mappingCacheHolder;
    }

    public HibernateMappingContext(
            HibernateConnectionSourceSettings settings, Object contextObject, Class<?>... persistentClasses) {
        this.mappingFactory = new HibernateMappingFactory();
        initialize(settings);
        this.mappingFactory.setDefaultMapping(settings.getDefault().getMapping());
        this.mappingFactory.setDefaultConstraints(settings.getDefault().getConstraints());
        this.mappingFactory.setContextObject(contextObject);
        this.syntaxStrategy = new GrailsJpaMappingConfigurationStrategy(mappingFactory);
        this.proxyFactory = new HibernateProxyHandler();
        addPersistentEntities(persistentClasses);
    }

    public HibernateMappingContext(HibernateConnectionSourceSettings settings, Class<?>... persistentClasses) {
        this(settings, null, persistentClasses);
    }

    public HibernateMappingContext() {
        this(new HibernateConnectionSourceSettings());
    }

    public void setDefaultConstraints(Closure<?> defaultConstraints) {
        this.mappingFactory.setDefaultConstraints(defaultConstraints);
    }

    @Override
    public MappingConfigurationStrategy getMappingSyntaxStrategy() {
        return syntaxStrategy;
    }

    @Override
    public MappingFactory<?,?> getMappingFactory() {
        return mappingFactory;
    }

    @Override
    protected PersistentEntity createPersistentEntity(Class<?> javaClass) {
        if (GormEntity.class.isAssignableFrom(javaClass)) {
            Object mappingStrategy = resolveMappingStrategy(javaClass);
            if (isValidMappingStrategy(javaClass, mappingStrategy)) {
                return new HibernatePersistentEntity(javaClass, this);
            }
        }
        return null;
    }

    @Override
    protected boolean isValidMappingStrategy(Class<?> javaClass, Object mappingStrategy) {
        return HibernateEntity.class.isAssignableFrom(javaClass)
                || super.isValidMappingStrategy(javaClass, mappingStrategy);
    }

    @Override
    protected PersistentEntity createPersistentEntity(Class<?> javaClass, boolean external) {
        return createPersistentEntity(javaClass);
    }

    @Override
    public PersistentEntity createEmbeddedEntity(Class<?> type) {
        HibernateEmbeddedPersistentEntity embedded = new HibernateEmbeddedPersistentEntity(type, this);
        embedded.initialize();
        return embedded;
    }

    @Override
    public PersistentEntity getPersistentEntity(String name) {
        final int proxyIndicator = name.indexOf("$HibernateProxy$");
        if (proxyIndicator > -1) {
            name = name.substring(0, proxyIndicator);
        }
        return super.getPersistentEntity(name);
    }

    public List<HibernatePersistentEntity> getHibernatePersistentEntities(String dataSourceName) {
        return persistentEntities.stream()
                .filter(HibernatePersistentEntity.class::isInstance)
                .map(HibernatePersistentEntity.class::cast)
                .peek(hibernateEntity -> hibernateEntity.setDataSourceName(dataSourceName))
                .toList();
    }
}
