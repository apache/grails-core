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
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14505')
class EnumMaxSizeColumnSpec extends HibernateGormDatastoreSpec {

    void setupSpec() {
        manager.registerDomainClasses(EnumSizedThing)
    }

    void "maxSize constrains the column of an enum property like it does a string property"() {
        when:
        Map<String, Integer> lengths = [:]
        manager.sessionFactory.currentSession.doWork { conn ->
            def rs = conn.createStatement().executeQuery(
                    "select column_name, character_maximum_length from information_schema.columns " +
                    "where lower(table_name) = 'enum_sized_thing'")
            while (rs.next()) {
                lengths[rs.getString(1).toLowerCase()] = rs.getInt(2)
            }
        }

        then:
        lengths['string_field'] == 100
        lengths['enum_field'] == 100
    }
}

enum EnumSizedKind { ONE, TWO }

@Entity
class EnumSizedThing {
    String stringField
    EnumSizedKind enumField

    static constraints = {
        stringField nullable: true, maxSize: 100
        enumField nullable: true, maxSize: 100
    }
}
