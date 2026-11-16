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

package org.grails.datastore.mapping.engine.event;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import org.grails.datastore.mapping.core.Datastore;
import org.grails.datastore.mapping.engine.EntityAccess;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;

/**
 * @author Burt Beckwith
 */
public class ValidationEvent extends AbstractPersistenceEvent {

    private static final long serialVersionUID = 1;

    private List<?> validatedFields;
    private boolean deepValidate = true;
    private transient List<BiPredicate<Object, Association>> cascadeListeners;

    public ValidationEvent(final Datastore source, final PersistentEntity entity,
            final EntityAccess entityAccess) {
        super(source, entity, entityAccess);
    }

    public ValidationEvent(final Datastore source, final Object entity) {
        super(source, entity);
    }

    public ValidationEvent(Object source, PersistentEntity entity, EntityAccess entityAccess) {
        super(source, entity, entityAccess);
    }

    public List<?> getValidatedFields() {
        return validatedFields;
    }

    public void setValidatedFields(final List<?> fields) {
        validatedFields = fields;
    }

    /**
     * @return Whether the associated objects are validated with the object of this event. They are, unless
     * {@code validate} or {@code save} is called with {@code deepValidate: false}.
     * @since 7.0.18
     */
    public boolean isDeepValidate() {
        return deepValidate;
    }

    /**
     * @param deepValidate Whether the associated objects are validated with the object of this event
     * @since 7.0.18
     */
    public void setDeepValidate(final boolean deepValidate) {
        this.deepValidate = deepValidate;
    }

    /**
     * Registers a listener for the associated objects that are validated with the object of this event, which get
     * no validation event of their own. The listener is called with each of them, and with the association through
     * which the validation reaches it, once the {@code beforeValidate} callback of that associated object has run and
     * before its constraints are checked. It returns whether it has changed the object. An object that it has left
     * alone is passed to it again when every associated object has been validated, since a callback that ran in
     * between may have changed that object. When the listener changes it then, its constraints are checked again,
     * and the errors that they recorded the first time are not reported.
     *
     * @param listener The listener
     * @since 7.0.18
     */
    public void addCascadeListener(final BiPredicate<Object, Association> listener) {
        if (cascadeListeners == null) {
            cascadeListeners = new ArrayList<>();
        }
        cascadeListeners.add(listener);
    }

    /**
     * @return A listener that calls every listener registered with {@link #addCascadeListener(BiPredicate)}, for the
     * validator to call with each associated object it validates, or {@code null} when none is registered
     * @since 7.0.18
     */
    public BiPredicate<Object, Association> getCascadeListener() {
        if (cascadeListeners == null) {
            return null;
        }
        final List<BiPredicate<Object, Association>> listeners = cascadeListeners;
        return (associatedObject, association) -> {
            boolean changed = false;
            for (BiPredicate<Object, Association> listener : listeners) {
                changed |= listener.test(associatedObject, association);
            }
            return changed;
        };
    }

    @Override
    public EventType getEventType() {
        return EventType.Validation;
    }
}
