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
package org.grails.orm.hibernate;

import java.util.Collections;

import org.hibernate.SessionFactory;

import org.springframework.transaction.support.TransactionSynchronizationManager;

import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSources;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.orm.hibernate.support.hibernate7.SessionHolder;

/**
 * A datastore for a specific connection in a multiple data source setup.
 */
public class ChildHibernateDatastore extends HibernateDatastore {

    private final HibernateDatastore parent;

    public ChildHibernateDatastore(
            HibernateDatastore parent,
            ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources,
            HibernateMappingContext mappingContext,
            ConfigurableApplicationEventPublisher eventPublisher) {
        super(connectionSources, mappingContext, eventPublisher,
            connectionSources.getDefaultConnectionSource().getSource());
        this.parent = parent;
    }

    @Override
    public HibernateDatastore getPrimaryDatastore() {
        return parent;
    }

    @Override
    protected HibernateGormEnhancer initialize() {
        return new HibernateGormEnhancer(this, transactionManager, connectionSources.getDefaultConnectionSource().getSettings(), Collections.emptyMap());
    }

    @Override
    public void destroy() {
        if (!this.destroyed) {
            super.destroy();
        }
    }

    @Override
    public HibernateDatastore getDatastoreForConnection(String connectionName) {
        if (Settings.SETTING_DATASOURCE.equals(connectionName) ||
                ConnectionSource.DEFAULT.equals(connectionName)) {
            return parent;
        } else {
            HibernateDatastore hibernateDatastore = parent.datastoresByConnectionSource.get(connectionName);
            if (hibernateDatastore == null) {
                throw new org.grails.datastore.mapping.core.exceptions.ConfigurationException(
                        "DataSource not found for name [" + connectionName +
                                "] in configuration. Please check your multiple data sources configuration and try again.");
            }
            return hibernateDatastore;
        }
    }

    /**
     * Returns a {@link HibernateSession} for this child datastore's {@link SessionFactory}.
     *
     * <p>When a Spring-managed transaction is active (e.g. inside {@code withNewTransaction}),
     * the transaction manager binds the Hibernate session to TSM with key = {@link SessionFactory}.
     * In that case we reuse that session so that any Hibernate filters enabled on it (e.g. the
     * DISCRIMINATOR multi-tenancy filter set by {@link org.grails.orm.hibernate.multitenancy.MultiTenantEventListener})
     * are visible to the query that {@code connect()} feeds.</p>
     *
     * <p>When no transaction session is bound (e.g. in SCHEMA mode where each child datastore
     * has its own session factory and sessions are created explicitly), we open a new session.
     * This preserves the original behaviour required by SCHEMA multi-tenancy.</p>
     *
     * <p>Session lifecycle is safe: {@link HibernateSession#disconnect()} closes
     * the {@code nativeSession} when it is non-null, and
     * {@code DatastoreUtils.closeSessionOrRegisterDeferredClose()} always delegates
     * to {@code disconnect()} for non-transactional sessions.</p>
     */
    @Override
    public Session connect() {
        SessionFactory sf = getSessionFactory();
        Object resource = TransactionSynchronizationManager.getResource(sf);
        if (resource instanceof SessionHolder sfHolder) {
            org.hibernate.Session nativeSession = sfHolder.getSession();
            if (nativeSession != null && nativeSession.isOpen()) {
                return new HibernateSession(this, sf, nativeSession);
            }
        }
        return new HibernateSession(this, sf, sf.openSession());
    }
}
