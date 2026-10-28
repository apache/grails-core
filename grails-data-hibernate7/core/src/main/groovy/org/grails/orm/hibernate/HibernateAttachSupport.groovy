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
package org.grails.orm.hibernate;

import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.TransientObjectException;
import org.hibernate.engine.internal.ForeignKeys;
import org.hibernate.engine.internal.Versioning;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.PersistenceContext;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.TypeHelper;

final class HibernateAttachSupport {

    private HibernateAttachSupport() {
    }

    static void attach(Object entity, Session session) {
        if (session.contains(entity)) {
            return;
        }

        SessionImplementor sessionImplementor = (SessionImplementor) session;
        PersistenceContext persistenceContext = sessionImplementor.getPersistenceContextInternal();
        Object target = persistenceContext.unproxyAndReassociate(entity);
        if (persistenceContext.getEntry(target) != null) {
            return;
        }

        EntityPersister persister = sessionImplementor.getEntityPersister(null, target);
        Object identifier = persister.getIdentifier(target, sessionImplementor);
        if (!ForeignKeys.isNotTransient(persister.getEntityName(), target, Boolean.FALSE, sessionImplementor)) {
            throw new TransientObjectException("cannot attach an unsaved transient instance: " + persister.getEntityName());
        }

        EntityKey entityKey = sessionImplementor.generateEntityKey(identifier, persister);
        persistenceContext.checkUniqueness(entityKey, target);

        Object[] loadedState = persister.getPropertyValues(target);
        TypeHelper.deepCopy(
                loadedState,
                persister.getPropertyTypes(),
                persister.getPropertyUpdateability(),
                loadedState,
                sessionImplementor);
        Object version = Versioning.getVersion(loadedState, persister);

        persistenceContext.addEntity(
                target,
                persister.isMutable() ? Status.MANAGED : Status.READ_ONLY,
                loadedState,
                entityKey,
                version,
                LockMode.NONE,
                true,
                persister,
                false);
        persister.afterReassociate(target, sessionImplementor);
    }
}
