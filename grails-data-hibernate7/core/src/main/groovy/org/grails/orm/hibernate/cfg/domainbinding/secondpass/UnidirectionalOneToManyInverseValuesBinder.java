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
package org.grails.orm.hibernate.cfg.domainbinding.secondpass;

import java.util.Optional;

import org.hibernate.FetchMode;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.ManyToOne;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/** Creates and binds a {@link ManyToOne} element for unidirectional to-many join-table associations. */
public class UnidirectionalOneToManyInverseValuesBinder {

    private final MetadataBuildingContext metadataBuildingContext;

    public UnidirectionalOneToManyInverseValuesBinder(MetadataBuildingContext metadataBuildingContext) {
        this.metadataBuildingContext = metadataBuildingContext;
    }

    public ManyToOne bind(HibernateToManyProperty property) {
        Collection collection = property.getCollection();
        ManyToOne manyToOne = new ManyToOne(metadataBuildingContext, collection.getCollectionTable());
        manyToOne.setIgnoreNotFound(property.getIgnoreNotFound());
        manyToOne.setLazy(property.isLazy());
        manyToOne.setReferencedEntityName(
                property.getHibernateAssociatedEntity().getName());
        return manyToOne;
    }
}
