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
import grails.gorm.hibernate.mapping.MappingBuilder
import org.apache.grails.data.hibernate5.core.GrailsDataHibernate5TckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import spock.lang.Issue

@Issue('https://github.com/apache/grails-core/issues/14497')
class IdentityLengthSpec extends GrailsDataTckSpec<GrailsDataHibernate5TckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([BuilderIdLength, ClosureIdLength])
    }

    private int idColumnLength(String table) {
        manager.hibernateSession.createSQLQuery(
                "select character_maximum_length from information_schema.columns where upper(table_name) = '${table}' and upper(column_name) = 'ID'"
        ).uniqueResult() as int
    }

    void 'the length given to the id in MappingBuilder.orm sizes the id column'() {
        expect:
        idColumnLength('BUILDER_ID_LENGTH') == 20
    }

    void 'the length given to the id in a mapping closure sizes the id column'() {
        expect:
        idColumnLength('CLOSURE_ID_LENGTH') == 20
    }
}

@Entity
class BuilderIdLength {
    String id

    static mapping = MappingBuilder.orm {
        id generator: 'assigned', length: 20
    }
}

@Entity
class ClosureIdLength {
    String id

    static mapping = {
        id generator: 'assigned', length: 20
    }
}
