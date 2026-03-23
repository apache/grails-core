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
package liquibase.ext.hibernate.database;

import java.util.Map;
import java.util.Optional;

import jakarta.persistence.spi.PersistenceUnitInfo;

import liquibase.database.DatabaseConnection;
import liquibase.ext.hibernate.database.connection.HibernateDriver;
import org.hibernate.jpa.boot.internal.EntityManagerFactoryBuilderImpl;
import org.hibernate.jpa.boot.spi.Bootstrap;

import org.springframework.orm.jpa.persistenceunit.DefaultPersistenceUnitManager;

/**
 * Database implementation for JPA configurations.
 * This supports passing a JPA persistence XML file reference.
 */
public class JpaPersistenceDatabase extends HibernateEjb3Database {

    @Override
    public boolean isCorrectDatabaseImplementation(DatabaseConnection conn) {
        return Optional.ofNullable(conn.getURL())
                .map(url -> url.startsWith("jpa:persistence:"))
                .orElse(false);
    }

    @Override
    public String getDefaultDriver(String url) {
        if (url != null && url.startsWith("jpa:persistence:")) {
            return HibernateDriver.class.getName();
        }
        return null;
    }

    @Override
    public String getShortName() {
        return "jpaPersistence";
    }

    @Override
    protected String getDefaultDatabaseProductName() {
        return "JPA Persistence";
    }

    @Override
    protected EntityManagerFactoryBuilderImpl createEntityManagerFactoryBuilder() {
        DefaultPersistenceUnitManager internalPersistenceUnitManager = new DefaultPersistenceUnitManager();

        String path = Optional.ofNullable(getHibernateConnection().getPath())
                .orElseThrow(() -> new IllegalStateException("Hibernate connection path is null"));

        internalPersistenceUnitManager.setPersistenceXmlLocation(path);

        internalPersistenceUnitManager.preparePersistenceUnitInfos();
        PersistenceUnitInfo persistenceUnitInfo = Optional.of(internalPersistenceUnitManager.obtainDefaultPersistenceUnitInfo())
                .orElseThrow(() -> new IllegalStateException("No persistence unit info found for path: " + path));

        return (EntityManagerFactoryBuilderImpl) Bootstrap.getEntityManagerFactoryBuilder(
                persistenceUnitInfo,
                Map.of(HibernateDatabase.HIBERNATE_TEMP_USE_JDBC_METADATA_DEFAULTS, Boolean.FALSE.toString()));
    }
}
