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

import java.util.Collections;
import java.util.Map;

/**
 * A datastore for a specific connection in a multiple data source setup.
 */
public class ChildHibernateDatastore extends HibernateDatastore {

    private static final ThreadLocal<HibernateDatastore> PARENT_HOLDER = new ThreadLocal<>();

    private final HibernateDatastore parent;

    public ChildHibernateDatastore(
            HibernateDatastore parent,
            ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources,
            HibernateMappingContext mappingContext,
            ConfigurableApplicationEventPublisher eventPublisher) {
        super(bindParent(parent, connectionSources), mappingContext, eventPublisher,
                connectionSources.getDefaultConnectionSource().getSource());
        this.parent = parent;
        PARENT_HOLDER.remove();
    }

    private static ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> bindParent(HibernateDatastore parent, ConnectionSources<SessionFactory, HibernateConnectionSourceSettings> connectionSources) {
        PARENT_HOLDER.set(parent);
        return connectionSources;
    }

    @Override
    public HibernateDatastore getPrimaryDatastore() {
        return parent != null ? parent : PARENT_HOLDER.get();
    }

    @Override
    protected HibernateGormEnhancer initialize() {
        HibernateDatastore p = getPrimaryDatastore();
        Map<String, HibernateDatastore> datastoresMap = p != null ? p.datastoresByConnectionSource : Collections.emptyMap();
        return new HibernateGormEnhancer(this, transactionManager, connectionSources.getDefaultConnectionSource().getSettings(), datastoresMap);
    }

    @Override
    public void destroy() {
        if (!this.destroyed) {
            super.destroy();
        }
    }

    @Override
    public HibernateDatastore getDatastoreForConnection(String connectionName) {
        String myName = getConnectionSources().getDefaultConnectionSource().getName();
        if (connectionName.equals(myName)) {
            return this;
        }

        HibernateDatastore p = getPrimaryDatastore();
        if (Settings.SETTING_DATASOURCE.equals(connectionName) || ConnectionSource.DEFAULT.equals(connectionName)) {
            return p;
        }

        if (p != null) {
            HibernateDatastore hibernateDatastore = p.datastoresByConnectionSource.get(connectionName);
            if (hibernateDatastore != null) {
                return hibernateDatastore;
            }
        }

        throw new org.grails.datastore.mapping.core.exceptions.ConfigurationException(
                "DataSource not found for name [" + connectionName +
                        "] in configuration. Please check your multiple data sources configuration and try again.");
    }

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
