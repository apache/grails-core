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
package org.grails.orm.hibernate.cfg.domainbinding.collectionType

import groovy.transform.CompileStatic
import org.hibernate.boot.spi.MetadataBuildingContext

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty

/** Collection holder. */
@CompileStatic
final class CollectionHolder {

    private final Map<Class<?>, CollectionType> map

    CollectionHolder(Map<Class<?>, CollectionType> map) {
        this.map = map
    }

    /** Creates a new {@link CollectionHolder} instance. */
    CollectionHolder(MetadataBuildingContext buildingContext) {
        this(Map.ofEntries(
                Map.entry(Set, new SetCollectionType(buildingContext)),
                Map.entry(SortedSet, new SetCollectionType(buildingContext)),
                Map.entry(List, new ListCollectionType(buildingContext)),
                Map.entry(Collection, new BagCollectionType(buildingContext)),
                Map.entry(Map, new MapCollectionType(buildingContext))))
    }

    Map<Class<?>, CollectionType> map() {
        return map
    }

    /** Get. */
    CollectionType get(Class<?> collectionClass) {
        return map.get(collectionClass)
    }

    org.hibernate.mapping.Collection create(HibernateToManyProperty property) {
        return map.get(property.type).create(property, property.persistentClass)
    }

}
