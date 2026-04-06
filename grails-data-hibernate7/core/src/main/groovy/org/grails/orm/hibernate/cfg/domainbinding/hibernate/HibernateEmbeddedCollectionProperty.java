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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate;

import java.beans.PropertyDescriptor;

import org.hibernate.mapping.Collection;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.types.mapping.EmbeddedCollectionWithMapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;

/**
 * Hibernate implementation of {@link org.grails.datastore.mapping.model.types.EmbeddedCollection}
 */
public class HibernateEmbeddedCollectionProperty extends EmbeddedCollectionWithMapping<PropertyConfig>
        implements HibernateToManyCollectionProperty {

    private Collection collection;

    public HibernateEmbeddedCollectionProperty(
            PersistentEntity entity, MappingContext context, PropertyDescriptor property) {
        super(entity, context, property);
    }

    @Override
    public Collection getHibernateCollection() {
        return collection;
    }

    @Override
    public void setHibernateCollection(Collection collection) {
        this.collection = collection;
    }
}
