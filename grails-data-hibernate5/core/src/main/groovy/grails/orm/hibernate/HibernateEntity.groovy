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

package grails.gorm.hibernate

import groovy.transform.CompileStatic

import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormEntity
import org.grails.orm.hibernate.AbstractHibernateGormStaticApi

/**
 * Extends the {@link GormEntity} trait adding additional Hibernate specific methods.
 * 
 * Note: Static methods for SQL queries are provided via {@link HibernateEntityStaticApi}
 * which is accessible via the static methods on implementing domain classes.
 *
 * @author Graeme Rocher
 * @since 6.1
 */
@CompileStatic
trait HibernateEntity<D> extends GormEntity<D> {

    // Note: Static SQL methods have been moved to AbstractHibernateGormStaticApi
    // and are accessible via GormEnhancer.findStaticApi(DomainClass).findAllWithSql(...) etc.
    // This change was required for Groovy 5 compatibility - traits with static methods
    // cause Java stub generation issues during joint compilation.
}
