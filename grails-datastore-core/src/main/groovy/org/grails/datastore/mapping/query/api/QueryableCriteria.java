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
package org.grails.datastore.mapping.query.api;

import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.query.Query;

import java.util.List;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
public interface QueryableCriteria<T> extends Criteria {

    /**
     * @return The target entity
     */
    PersistentEntity getPersistentEntity();
    /**
     * @return A list of all criteria
     */
    List<Query.Criterion> getCriteria();

    List<Query.Projection> getProjections();

    /**
     * @return Find a single result
     */
    T find();

    /**
     * List all results
     * @return All results
     */
    List<T> list();

    /**
     * @return The alias to be used for the query, null if none
     */
    String getAlias();

}
