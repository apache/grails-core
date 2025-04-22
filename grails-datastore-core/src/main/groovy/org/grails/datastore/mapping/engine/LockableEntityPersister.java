/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.mapping.engine;

import java.io.Serializable;

import org.grails.datastore.mapping.model.PersistentProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.CannotAcquireLockException;
import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

/**
 * Abstract base class for entity persisters that support locking.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class LockableEntityPersister extends EntityPersister {

    public static int DEFAULT_TIMEOUT = 30;

    public LockableEntityPersister(MappingContext mappingContext, PersistentEntity entity,
               Session session, ApplicationEventPublisher publisher) {
        super(mappingContext, entity, session, publisher);
    }

    /**
     * Locks an object for the given identifier returning the locked instance
     *
     * @param id The identifier
     * @return The locked object
     * @throws CannotAcquireLockException Thrown if a lock couldn't be acquired before the default timeout elapsed
     */
    public abstract Object lock(Serializable id) throws CannotAcquireLockException;

    /**
     * Acquire a lock using the given identifier and timeout delay
     * @param id the identifier
     * @param timeout the amount of time to wait before giving up in seconds
     * @return The locked object
     * @throws CannotAcquireLockException
     */
    public abstract Object lock(Serializable id, int timeout) throws CannotAcquireLockException;

    /**
     * Return whether an object is locked or not
     * @param o The object
     * @return  True if it is locked
     */
    public abstract boolean isLocked(Object o);

    /**
     * Unlocks a locked object
     * @param o The object to unlock
     */
    public abstract void unlock(Object o);

    @Override
    @SuppressWarnings("unchecked")
    public Object proxy(Serializable key) {
        PersistentEntity entity = getPersistentEntity();
        PersistentProperty identity = entity.getIdentity();
        if(identity != null) {
            key = (Serializable) getMappingContext().getConversionService().convert(key, identity.getType());
        }
        return getProxyFactory().createProxy(session, entity.getJavaClass(), key);
    }
}
