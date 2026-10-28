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
package org.grails.orm.hibernate.cfg.domainbinding.collectionType;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

/**
 * A Collection type, for the moment only Set is supported
 *
 * @author Graeme
 */
public abstract class CollectionType {

    /** The clazz. */
    protected final Class<?> clazz;

    /** The building context. */
    protected final MetadataBuildingContext buildingContext;

    /** Creates a new {@link CollectionType} instance. */
    protected CollectionType(Class<?> clazz, MetadataBuildingContext buildingContext) {
        this.clazz = clazz;
        this.buildingContext = buildingContext;
    }

    /** Create collection. */
    public abstract Collection createCollection(PersistentClass owner);

    /** Create. */
    public Collection create(HibernateToManyProperty property, PersistentClass owner) throws MappingException {
        Collection coll = createCollection(owner);
        coll.setCollectionTable(owner.getTable());
        String typeName = getTypeName(property);
        if (typeName != null && !clazz.getName().equals(typeName)) {
            coll.setTypeName(typeName);
        }
        return coll;
    }

    @Override
    public String toString() {
        return clazz.getName();
    }

    /** Gets the type name. */
    public String getTypeName(HibernateToManyProperty property) {
        return property.getTypeName();
    }
}
