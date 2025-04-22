/* Copyright (C) 2011 SpringSource
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
package org.grails.datastore.mapping.query;

import java.util.List;

import org.grails.datastore.mapping.core.Session;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.query.Query.Criterion;

/**
 * Used to capture the metadata for a query on an associated object.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public class AssociationQuery extends Query implements Criterion {

    private Association<?> association;

    protected AssociationQuery(Session session, PersistentEntity entity, Association association) {
        super(session, entity);
        this.association = association;
    }

    /**
     * The association being queried
     *
     * @return The association
     */
    public Association<?> getAssociation() {
        return association;
    }

    @Override
    protected List executeQuery(PersistentEntity e, Junction j) {
        throw new UnsupportedOperationException("AssociationQuery instances are not executable and are merely metadata defined to query associations in a primary query");
    }
}
