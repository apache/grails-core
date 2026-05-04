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

import org.grails.datastore.gorm.events.ConfigurableApplicationEventPublisher;
import org.grails.datastore.mapping.core.connections.ConnectionSource;
import org.grails.datastore.mapping.core.connections.ConnectionSources;
import org.grails.orm.hibernate.cfg.HibernateMappingContext;
import org.grails.orm.hibernate.cfg.Settings;
import org.grails.orm.hibernate.connections.HibernateConnectionSourceSettings;
import org.grails.datastore.mapping.core.connections.SingletonConnectionSources;
import java.util.Collections;

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
}
