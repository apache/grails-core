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

package org.grails.datastore.mapping.core.impl;

import org.grails.datastore.mapping.engine.EntityAccess;

/**
 * An insert that is pending execution in a flush() operation
 *
 * @param <E> The native entry to persist
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface PendingInsert<E, K> extends Runnable, PendingOperation<E, K>{
    /**
     * @return The EntityAccess object for the entity to be inserted
     */
    EntityAccess getEntityAccess();
}
