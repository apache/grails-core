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
package org.grails.forge.analytics.postgres.gcp;

import io.micronaut.configuration.jdbc.hikari.DatasourceConfiguration;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.context.event.BeanCreatedEvent;
import io.micronaut.context.event.BeanCreatedEventListener;

import jakarta.inject.Singleton;

/**
 * Configuration for the Cloud SQL environment.
 *
 * @author graemerocher
 * @since 6.0.0
 */
@Singleton
@Requires(env = Environment.GOOGLE_COMPUTE)
@Requires(property = GoogleCloudSqlSetup.CLOUD_SQL_CONNECTION_NAME)
public class GoogleCloudSqlSetup implements BeanCreatedEventListener<DatasourceConfiguration> {
    public static final String CLOUD_SQL_CONNECTION_NAME = "cloud.sql.connection.name";
    private static final String DB_NAME = System.getenv("DB_NAME");
    private final String cloudSqlInstance;

    public GoogleCloudSqlSetup(@Property(name = CLOUD_SQL_CONNECTION_NAME) String cloudSqlInstance) {
        this.cloudSqlInstance = cloudSqlInstance;
    }

    @Override
    public DatasourceConfiguration onCreated(BeanCreatedEvent<DatasourceConfiguration> event) {
        DatasourceConfiguration config = event.getBean();
        if (DB_NAME != null) {
            config.setJdbcUrl(String.format("jdbc:postgresql:///%s", DB_NAME));
        }
        config.addDataSourceProperty("socketFactory", "com.google.cloud.sql.postgres.SocketFactory");
        config.addDataSourceProperty("cloudSqlInstance", cloudSqlInstance);
        return config;
    }
}
