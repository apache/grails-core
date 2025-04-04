/*
 * Copyright 2013 the original author or authors.
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
import org.hibernate.FlushMode
import org.hibernate.Session

@CompileStatic
class HibernateGormValidationApi<D> extends AbstractHibernateGormValidationApi<D> {

    HibernateGormValidationApi(Class<D> persistentClass, HibernateDatastore datastore, ClassLoader classLoader) {
        super(persistentClass, datastore, classLoader)
        hibernateTemplate = new GrailsHibernateTemplate(datastore.getSessionFactory(), datastore)
    }

    @Override
    void restoreFlushMode(Session session, Object previousFlushMode) {
        if(previousFlushMode != null) {
            session.setHibernateFlushMode((FlushMode)previousFlushMode)
        }
    }

    @Override
    Object readPreviousFlushMode(Session session) {
        return session.getHibernateFlushMode()
    }

    @Override
    def applyManualFlush(Session session) {
        session.setHibernateFlushMode(FlushMode.MANUAL)
    }
}
