/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import groovy.transform.CompileStatic

/**
 * Enum representing the supported keywords in the Hibernate ORM mapping DSL.
 *
 * @author walter.duquedeestrada
 * @since 7.0
 */
@CompileStatic
enum HibernateMappingKeyword {

    INCLUDES('includes'),
    HIBERNATE_CUSTOM_USER_TYPE('hibernateCustomUserType'),
    TABLE('table'),
    DISCRIMINATOR('discriminator'),
    AUTO_IMPORT('autoImport'),
    SORT('sort'),
    AUTOWIRE('autowire'),
    DYNAMIC_UPDATE('dynamicUpdate'),
    DYNAMIC_INSERT('dynamicInsert'),
    BATCH_SIZE('batchSize'),
    ORDER('order'),
    AUTO_TIMESTAMP('autoTimestamp'),
    VERSION('version'),
    TENANT_ID('tenantId'),
    CACHE('cache'),
    TABLE_PER_HIERARCHY('tablePerHierarchy'),
    TABLE_PER_SUBCLASS('tablePerSubclass'),
    TABLE_PER_CONCRETE_CLASS('tablePerConcreteClass'),
    ID('id'),
    PROPERTY('property'),
    COLUMNS('columns'),
    DATASOURCE('datasource'),
    DATASOURCES('datasources'),
    COMMENT('comment'),
    USER_TYPE('user-type'),
    IMPORT_FROM('importFrom')

    private final String keyword

    HibernateMappingKeyword(String keyword) {
        this.keyword = keyword
    }

    String getKeyword() {
        return keyword
    }

    @Override
    String toString() {
        return keyword
    }

    private static final Map<String, HibernateMappingKeyword> KEYWORDS = values().collectEntries { [it.keyword, it] }

    static HibernateMappingKeyword fromString(String name) {
        return KEYWORDS[name]
    }
}
