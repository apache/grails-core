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
package org.grails.orm.hibernate.persister;

import org.hibernate.mapping.Collection;
import org.hibernate.mapping.IndexedCollection;
import org.hibernate.mapping.ManyToOne;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.internal.StandardPersisterClassResolver;

/**
 * Resolves the persister of a set or a bag of entities in a join table to a {@link JoinTableCollectionPersister}, and
 * every other persister the way Hibernate does.
 *
 * @since 8.0.1
 */
public class GormPersisterClassResolver extends StandardPersisterClassResolver {

    @Override
    public Class<? extends CollectionPersister> getCollectionPersisterClass(Collection metadata) {
        if (!metadata.isOneToMany() &&
                metadata.getElement() instanceof ManyToOne &&
                !metadata.isIdentified() &&
                metadata.getCustomSQLDelete() == null &&
                metadata.getStateManagementType() == null &&
                metadata.getSoftDeleteColumn() == null &&
                metadata.getAuxiliaryTable() == null &&
                !(metadata instanceof IndexedCollection)) {
            return JoinTableCollectionPersister.class;
        }
        return super.getCollectionPersisterClass(metadata);
    }
}
