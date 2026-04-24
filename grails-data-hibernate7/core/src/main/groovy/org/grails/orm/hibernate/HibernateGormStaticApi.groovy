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

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.DatastoreResolver
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.finders.FinderMethod
import org.grails.datastore.mapping.model.MappingContext
import org.grails.orm.hibernate.GrailsHibernateTemplate
import org.springframework.transaction.PlatformTransactionManager

/**
 * Hibernate GORM static API.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormStaticApi<D> extends GormStaticApi<D> {

    protected GrailsHibernateTemplate hibernateTemplate

    HibernateGormStaticApi(Class<D> persistentClass, HibernateDatastore datastore, List<FinderMethod> finders, String qualifier) {
        super(persistentClass, datastore, finders, qualifier)
        this.hibernateTemplate = new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
    }

    HibernateGormStaticApi(Class<D> persistentClass, MappingContext mappingContext, List<FinderMethod> finders, DatastoreResolver datastoreResolver, String qualifier) {
        super(persistentClass, mappingContext, finders, datastoreResolver, qualifier)
    }

    protected GrailsHibernateTemplate getHibernateTemplate() {
        if (this.hibernateTemplate == null) {
            HibernateDatastore datastore = (HibernateDatastore) getDatastore()
            return new GrailsHibernateTemplate(datastore.sessionFactory, datastore)
        }
        return hibernateTemplate
    }
}
