/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import spock.lang.Specification
import spock.lang.Unroll

class HibernateMappingKeywordSpec extends Specification {

    @Unroll
    def "getKeyword returns '#expected' for #name"() {
        expect:
        HibernateMappingKeyword.valueOf(name).getKeyword() == expected

        where:
        name                      | expected
        'INCLUDES'                | 'includes'
        'TABLE'                   | 'table'
        'DISCRIMINATOR'           | 'discriminator'
        'AUTO_IMPORT'             | 'autoImport'
        'SORT'                    | 'sort'
        'CACHE'                   | 'cache'
        'ID'                      | 'id'
        'VERSION'                 | 'version'
        'TENANT_ID'               | 'tenantId'
        'BATCH_SIZE'              | 'batchSize'
        'COMMENT'                 | 'comment'
        'DATASOURCE'              | 'datasource'
        'DATASOURCES'             | 'datasources'
    }

    @Unroll
    def "toString returns '#expected' for #name"() {
        expect:
        HibernateMappingKeyword.valueOf(name).toString() == expected

        where:
        name            | expected
        'TABLE'         | 'table'
        'ID'            | 'id'
        'CACHE'         | 'cache'
    }

    @Unroll
    def "fromString('#keyword') returns expected enum"() {
        expect:
        HibernateMappingKeyword.fromString(keyword) == expected

        where:
        keyword                  | expected
        'table'                  | HibernateMappingKeyword.TABLE
        'id'                     | HibernateMappingKeyword.ID
        'cache'                  | HibernateMappingKeyword.CACHE
        'includes'               | HibernateMappingKeyword.INCLUDES
        'discriminator'          | HibernateMappingKeyword.DISCRIMINATOR
        'autoImport'             | HibernateMappingKeyword.AUTO_IMPORT
        'hibernateCustomUserType'| HibernateMappingKeyword.HIBERNATE_CUSTOM_USER_TYPE
        'sort'                   | HibernateMappingKeyword.SORT
        'autowire'               | HibernateMappingKeyword.AUTOWIRE
        'dynamicUpdate'          | HibernateMappingKeyword.DYNAMIC_UPDATE
        'dynamicInsert'          | HibernateMappingKeyword.DYNAMIC_INSERT
        'batchSize'              | HibernateMappingKeyword.BATCH_SIZE
        'order'                  | HibernateMappingKeyword.ORDER
        'autoTimestamp'          | HibernateMappingKeyword.AUTO_TIMESTAMP
        'version'                | HibernateMappingKeyword.VERSION
        'tenantId'               | HibernateMappingKeyword.TENANT_ID
        'tablePerHierarchy'      | HibernateMappingKeyword.TABLE_PER_HIERARCHY
        'tablePerSubclass'       | HibernateMappingKeyword.TABLE_PER_SUBCLASS
        'tablePerConcreteClass'  | HibernateMappingKeyword.TABLE_PER_CONCRETE_CLASS
        'property'               | HibernateMappingKeyword.PROPERTY
        'columns'                | HibernateMappingKeyword.COLUMNS
        'datasource'             | HibernateMappingKeyword.DATASOURCE
        'datasources'            | HibernateMappingKeyword.DATASOURCES
        'comment'                | HibernateMappingKeyword.COMMENT
        'user-type'              | HibernateMappingKeyword.USER_TYPE
        'importFrom'             | HibernateMappingKeyword.IMPORT_FROM
        'unknown'                | null
    }

    def 'all enum constants have non-null keywords'() {
        expect:
        HibernateMappingKeyword.values().every { it.keyword != null }
    }

    def 'fromString returns null for unknown keyword'() {
        expect:
        HibernateMappingKeyword.fromString('nonExistentKeyword') == null
    }

    def 'fromString returns null for empty string'() {
        expect:
        HibernateMappingKeyword.fromString('') == null
    }
}
