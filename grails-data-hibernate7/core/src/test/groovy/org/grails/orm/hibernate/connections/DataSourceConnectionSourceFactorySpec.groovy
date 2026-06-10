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
package org.grails.orm.hibernate.connections

import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.datastore.mapping.core.connections.ConnectionSource
import org.grails.datastore.gorm.jdbc.connections.DataSourceConnectionSourceFactory
import org.grails.datastore.gorm.jdbc.schema.DefaultSchemaHandler
import org.hibernate.dialect.Oracle8iDialect
import spock.lang.Specification

/**
 * Created by graemerocher on 05/07/16.
 */
class DataSourceConnectionSourceFactorySpec extends Specification {

    void "test datasource connection source factory"() {
        when:
        DataSourceConnectionSourceFactory factory = new DataSourceConnectionSourceFactory()
        Map config = [
                'dataSource.url':"jdbc:h2:mem:dsConnDsFactorySpecDb;LOCK_TIMEOUT=10000",
                'dataSource.dbCreate': 'update',
                'dataSource.dialect': Oracle8iDialect.name,
                'dataSource.properties.dbProperties': [useSSL: false]
        ]
        def connectionSource = factory.create(ConnectionSource.DEFAULT, DatastoreUtils.createPropertyResolver(config))

        then:"The connection source is correct"
        connectionSource.name == ConnectionSource.DEFAULT
        connectionSource.source

        when:"The schema names are resolved"
        def schemaNames = new DefaultSchemaHandler().resolveSchemaNames(connectionSource.source)

        then:"They are correct"
        schemaNames == ['INFORMATION_SCHEMA', 'PUBLIC']
    }
}
