/* Copyright (C) 2011 SpringSource
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
package org.grails.orm.hibernate

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormInstanceApi
import org.grails.datastore.gorm.GormStaticApi
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.springframework.transaction.PlatformTransactionManager
/**
 * Extended GORM Enhancer that fills out the remaining GORM for Hibernate methods
 * and implements string-based query support via HQL.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class HibernateGormEnhancer extends GormEnhancer {

    @Deprecated
    HibernateGormEnhancer(HibernateDatastore datastore, PlatformTransactionManager transactionManager) {
        super(datastore, transactionManager)
    }

    HibernateGormEnhancer(Datastore datastore, PlatformTransactionManager transactionManager, ConnectionSourceSettings settings) {
        super(datastore, transactionManager, settings)
    }

    @Override
    protected <D> GormStaticApi<D> getStaticApi(Class<D> cls, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        HibernateDatastore datastoreForConnection = hibernateDatastore.getDatastoreForConnection(qualifier)
        new HibernateGormStaticApi<D>(
                cls,
                datastoreForConnection,
                createDynamicFinders(datastoreForConnection),
                Thread.currentThread().contextClassLoader,
                datastoreForConnection.getTransactionManager()
        )
    }

    @Override
    protected <D> GormInstanceApi<D> getInstanceApi(Class<D> cls, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormInstanceApi<D>(cls, hibernateDatastore.getDatastoreForConnection(qualifier), Thread.currentThread().contextClassLoader)
    }

    @Override
    protected <D> GormValidationApi<D> getValidationApi(Class<D> cls, String qualifier) {
        HibernateDatastore hibernateDatastore = (HibernateDatastore) datastore
        new HibernateGormValidationApi<D>(cls, hibernateDatastore.getDatastoreForConnection(qualifier), Thread.currentThread().contextClassLoader)
    }

    @Override
    protected void registerConstraints(Datastore datastore) {
        // no-op
    }
}
