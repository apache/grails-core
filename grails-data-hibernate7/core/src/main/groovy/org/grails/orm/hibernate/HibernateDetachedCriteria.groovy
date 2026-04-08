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
package org.grails.orm.hibernate

import groovy.transform.CompileDynamic

import grails.gorm.DetachedCriteria
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.orm.hibernate.query.PropertyReference

/**
 * Hibernate-specific subclass of {@link DetachedCriteria} that overrides
 * {@code propertyMissing} to return a {@link PropertyReference} for numeric
 * persistent properties. This enables cross-property arithmetic in where-DSL
 * expressions such as {@code pageCount > price * 10} without touching shared
 * modules (and therefore without affecting H5 or MongoDB backends).
 */
@CompileDynamic
class HibernateDetachedCriteria<T> extends DetachedCriteria<T> {

    HibernateDetachedCriteria(Class<T> targetClass, String alias = null) {
        super(targetClass, alias)
    }

    @Override
    protected HibernateDetachedCriteria<T> newInstance() {
        new HibernateDetachedCriteria<T>(targetClass, alias)
    }

    @Override
    def propertyMissing(String name) {
        PersistentProperty prop = getPersistentEntity()?.getPropertyByName(name)
        if (prop != null && Number.isAssignableFrom(prop.type)) {
            return new PropertyReference(name)
        }
        super.propertyMissing(name)
    }
}
