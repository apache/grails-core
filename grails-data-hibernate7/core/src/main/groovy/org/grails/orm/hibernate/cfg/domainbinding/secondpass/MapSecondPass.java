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

import java.io.Serial;
import java.util.Map;

import org.hibernate.MappingException;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

@SuppressWarnings({"PMD.NonSerializableClass", "deprecation"})
public class MapSecondPass implements org.hibernate.boot.spi.SecondPass, GrailsSecondPass, java.io.Serializable {

    @Serial
    private static final long serialVersionUID = -3244991685626409031L;

    protected final HibernateToManyProperty property;
    private final org.hibernate.mapping.Collection collection;
    private final MapSecondPassBinder mapSecondPassBinder;

    public MapSecondPass(MapSecondPassBinder mapSecondPassBinder, HibernateToManyProperty property) {
        this(mapSecondPassBinder, property, null);
    }

    /**
     * @param collection the collection this pass binds. The same property can be bound more than once, for instance
     * when its owner is an entity that is also embedded in another, and each binding has its own collection.
     */
    public MapSecondPass(MapSecondPassBinder mapSecondPassBinder, HibernateToManyProperty property, org.hibernate.mapping.Collection collection) {
        this.mapSecondPassBinder = mapSecondPassBinder;
        this.property = property;
        this.collection = collection;
    }

    @Override
    public void doSecondPass(Map persistentClasses) throws MappingException {
        if (collection != null) {
            property.setHibernateCollection(collection);
        }
        mapSecondPassBinder.bindMapSecondPass(property);
    }
}
