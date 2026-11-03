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

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.grails.orm.hibernate.cfg.Settings
import spock.lang.Issue
import spock.lang.Specification

@Issue('https://github.com/apache/grails-core/issues/14427')
class JoinedSubclassSchemaSpec extends Specification {

    void "a joined subclass is created in the schema it declares"() {
        given:
        HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(
                (Settings.SETTING_DB_CREATE): 'create-drop',
                'dataSource.url': 'jdbc:h2:mem:joinedSubclassSchema;INIT=CREATE SCHEMA IF NOT EXISTS agis;LOCK_TIMEOUT=10000'),
                SchemaSchoolYear, SchemaSchoolYearRegular)

        when:
        List<String> tables = []
        datastore.sessionFactory.openSession().withCloseable { session ->
            session.doWork { conn ->
                def rs = conn.createStatement().executeQuery(
                        "select table_schema, table_name from information_schema.tables where table_name like 'SCHEMA_SCHOOL_YEAR%'")
                while (rs.next()) {
                    tables << "${rs.getString(1)}.${rs.getString(2)}".toString()
                }
            }
        }

        then: 'the root and the subclass table are both in the declared schema'
        tables.sort() == ['AGIS.SCHEMA_SCHOOL_YEAR', 'AGIS.SCHEMA_SCHOOL_YEAR_REGULAR']

        cleanup:
        datastore?.close()
    }
}

@Entity
class SchemaSchoolYear {
    String name

    static mapping = {
        table schema: 'agis'
        tablePerHierarchy false
    }
}

@Entity
class SchemaSchoolYearRegular extends SchemaSchoolYear {
    String extra

    static mapping = {
        table schema: 'agis'
    }
}
