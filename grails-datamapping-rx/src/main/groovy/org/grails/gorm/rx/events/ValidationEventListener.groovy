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

package org.grails.gorm.rx.events

import groovy.transform.CompileStatic
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.engine.event.AbstractPersistenceEvent
import org.grails.datastore.mapping.engine.event.PersistenceEventListener
import org.grails.datastore.mapping.engine.event.PreInsertEvent
import org.grails.datastore.mapping.engine.event.PreUpdateEvent
import org.grails.datastore.rx.RxDatastoreClient
import org.springframework.context.ApplicationEvent
/**
 * A validation event listener for RxGORM
 *
 * @author Graeme Rocher
 * @since 6.0
 */
@CompileStatic
class ValidationEventListener implements PersistenceEventListener {
    @Override
    boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
        return PreInsertEvent.isAssignableFrom(eventType) || PreUpdateEvent.isAssignableFrom(eventType)
    }

    @Override
    boolean supportsSourceType(Class<?> sourceType) {
        return RxDatastoreClient.isAssignableFrom(sourceType)
    }

    @Override
    void onApplicationEvent(ApplicationEvent event) {
        def persistenceEvent = (AbstractPersistenceEvent) event
        def entityObject = persistenceEvent.getEntityObject()
        if(entityObject instanceof GormValidateable) {
            GormValidateable gormValidateable = (GormValidateable) entityObject
            if(gormValidateable.shouldSkipValidation()) {
                if( gormValidateable.getErrors()?.hasErrors() )  {
                    persistenceEvent.cancel()
                }
            }
            else {
                if( !gormValidateable.validate() ) {
                    persistenceEvent.cancel()
                }
            }
        }
    }

    @Override
    int getOrder() {
        return 0
    }
}
