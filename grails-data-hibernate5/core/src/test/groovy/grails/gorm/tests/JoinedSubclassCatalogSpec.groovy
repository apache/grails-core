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

package grails.gorm.tests

import java.sql.Connection
import java.sql.DriverManager

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import org.testcontainers.mariadb.MariaDBContainer
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * MariaDB reads the catalog of a table as the database it is created in, so it is the database the catalog branch can
 * be observed on.
 */
@Issue('https://github.com/apache/grails-core/issues/14427')
class JoinedSubclassCatalogSpec extends Specification {

    @Shared
    MariaDBContainer mariadb = new MariaDBContainer('mariadb:10.11')

    void setupSpec() {
        mariadb.start()
        DriverManager.getConnection(mariadb.jdbcUrl, 'root', mariadb.password).withCloseable { Connection conn ->
            conn.createStatement().execute('create database if not exists joined_catalog')
        }
    }

    void cleanupSpec() {
        mariadb.stop()
    }

    void "a joined subclass is created in the catalog it declares"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(
                (Settings.SETTING_DB_CREATE): 'create-drop',
                'dataSource.url': mariadb.jdbcUrl,
                'dataSource.username': 'root',
                'dataSource.password': mariadb.password,
                'dataSource.driverClassName': 'org.mariadb.jdbc.Driver',
                'hibernate.dialect': 'org.hibernate.dialect.MariaDB103Dialect'),
                CatalogYear, CatalogYearRegular)

        when:
        List<String> tables = []
        DriverManager.getConnection(mariadb.jdbcUrl, 'root', mariadb.password).withCloseable { Connection conn ->
            def rs = conn.createStatement().executeQuery(
                    "select table_schema, table_name from information_schema.tables where table_name like 'catalog_year%'")
            while (rs.next()) {
                tables << "${rs.getString(1)}.${rs.getString(2)}".toString()
            }
        }

        then: 'the root and the subclass table are both in the declared catalog'
        tables.sort() == ['joined_catalog.catalog_year', 'joined_catalog.catalog_year_regular']

        cleanup:
        datastore?.close()
    }
}

@Entity
class CatalogYear {
    String name

    static mapping = {
        table catalog: 'joined_catalog'
        tablePerHierarchy false
    }
}

@Entity
class CatalogYearRegular extends CatalogYear {
    String extra

    static mapping = {
        table catalog: 'joined_catalog'
    }
}
