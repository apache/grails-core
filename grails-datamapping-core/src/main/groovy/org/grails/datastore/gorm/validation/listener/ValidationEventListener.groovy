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

package org.grails.datastore.gorm.validation.listener

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.gorm.GormValidationApi
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.core.Session
import org.grails.datastore.mapping.core.connections.ConnectionSourcesProvider
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.springframework.context.ApplicationEvent

import jakarta.persistence.FlushModeType

/**
 * An event listener for ensuring entities are valid before saving or updating
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class ValidationEventListener extends AbstractPersistenceEventListener{
    ValidationEventListener(Datastore datastore) {
        super(datastore)
    }

    @Override
    protected void onPersistenceEvent(AbstractPersistenceEvent event) {
        def entityObject = event.getEntityObject()
        if(entityObject instanceof GormValidateable) {
            GormValidateable gormValidateable = (GormValidateable) entityObject
            if(gormValidateable.shouldSkipValidation()) {
                if( gormValidateable.getErrors()?.hasErrors() )  {
                    event.cancel()
                }
            }
            else {

                Datastore source = (Datastore)event.getSource()

                Session currentSession = source.currentSession
                FlushModeType previousFlushMode = currentSession.flushMode
                try {
                    currentSession.setFlushMode(FlushModeType.COMMIT)
                    boolean hasErrors = false
                    if(source instanceof ConnectionSourcesProvider) {
                        def connectionSourceName = ((ConnectionSourcesProvider) source).connectionSources.defaultConnectionSource.name
                        GormValidationApi validationApi = GormEnhancer.findValidationApi((Class<Object>)entityObject.getClass(), connectionSourceName)
                        hasErrors = !validationApi.validate((Object)entityObject)
                    }
                    else {
                        hasErrors = !gormValidateable.validate()
                    }
                    if( hasErrors ) {
                        event.cancel()
                    }
                } finally {
                    currentSession.setFlushMode(previousFlushMode)
                }
            }
        }
    }

    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreInsertEvent.isAssignableFrom(eventType) || PreUpdateEvent.isAssignableFrom(eventType)
    }
}
