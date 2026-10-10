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
package org.grails.datastore.mapping.engine.event

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationEvent

import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.engine.EntityAccess
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * @author Burt Beckwith
 */
@CompileStatic
@SuppressWarnings('serial')
abstract class AbstractPersistenceEvent extends ApplicationEvent {

    public static final String ONLOAD_EVENT = 'onLoad'
    public static final String ONLOAD_SAVE = 'onSave'
    public static final String BEFORE_LOAD_EVENT = 'beforeLoad'
    public static final String BEFORE_INSERT_EVENT = 'beforeInsert'
    public static final String AFTER_INSERT_EVENT = 'afterInsert'
    public static final String BEFORE_UPDATE_EVENT = 'beforeUpdate'
    public static final String AFTER_UPDATE_EVENT = 'afterUpdate'
    public static final String BEFORE_DELETE_EVENT = 'beforeDelete'
    public static final String AFTER_DELETE_EVENT = 'afterDelete'
    public static final String AFTER_LOAD_EVENT = 'afterLoad'

    private final PersistentEntity entity
    private final Object entityObject
    private final EntityAccess entityAccess
    private boolean cancelled
    private List<String> excludedListenerNames = new ArrayList<>()
    private Serializable nativeEvent

    protected AbstractPersistenceEvent(final Datastore source, final PersistentEntity entity,
            final EntityAccess entityAccess) {
        this((Object) source, entity, entityAccess)
    }

    protected AbstractPersistenceEvent(final Object source, final PersistentEntity entity,
                                       final EntityAccess entityAccess) {
        super(source)
        this.entity = entity
        this.entityAccess = entityAccess
        this.entityObject = entityAccess != null ? entityAccess.getEntity() : null
    }

    protected AbstractPersistenceEvent(final Object source, final PersistentEntity entity) {
        this(source, entity, (EntityAccess) null)
    }

    protected AbstractPersistenceEvent(final Datastore source, final Object entity) {
        super(source)
        MappingContext mappingContext = source.getMappingContext()
        Object unwrapped = mappingContext.getProxyHandler().unwrap(entity)
        PersistentEntity resolvedEntity = mappingContext.getPersistentEntity(unwrapped.getClass().getName())
        this.entityObject = unwrapped
        this.entity = resolvedEntity
        this.entityAccess = resolvedEntity != null ? mappingContext.createEntityAccess(resolvedEntity, unwrapped) : null
    }

    Object getEntityObject() {
        return entityObject
    }

    PersistentEntity getEntity() {
        return entity
    }

    EntityAccess getEntityAccess() {
        return entityAccess
    }

    void cancel() {
        cancelled = true
    }

    boolean isCancelled() {
        return cancelled
    }

    void addExcludedListenerName(final String name) {
        excludedListenerNames.add(name)
    }

    boolean isListenerExcluded(final String name) {
        return excludedListenerNames.contains(name)
    }

    void setNativeEvent(final Serializable nativeEvent) {
        this.nativeEvent = nativeEvent
    }

    Serializable getNativeEvent() {
        return nativeEvent
    }

    abstract EventType getEventType()

}
