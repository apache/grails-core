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
package org.grails.datastore.mapping.model;

import org.grails.datastore.mapping.config.Entity;

/**
 * A class mapping is a mapping between a class and some external
 * form such as a table, column family, or document (depending on the underlying data store).
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ClassMapping<T extends Entity> {

    /**
     * Obtains the PersistentEntity for this class mapping
     *
     * @return The PersistentEntity
     */
    PersistentEntity getEntity();

    /**
     * Returns the mapped form of the class such as a Table, a Key Space, Document etc.
     * @return The mapped representation
     */
    T getMappedForm();

    /**
     * Returns details of the identifier used for this class
     *
     * @return The Identity
     */
    IdentityMapping getIdentifier();
}
