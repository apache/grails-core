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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

import org.hibernate.boot.spi.MetadataBuildingContext;

/** Collection holder. */
public record CollectionHolder(Map<Class<?>, CollectionType> map) {

    /** Creates a new {@link CollectionHolder} instance. */
    public CollectionHolder(MetadataBuildingContext buildingContext) {
        this(Map.ofEntries(
                Map.entry(Set.class, new SetCollectionType(buildingContext)),
                Map.entry(SortedSet.class, new SetCollectionType(buildingContext)),
                Map.entry(List.class, new ListCollectionType(buildingContext)),
                Map.entry(Collection.class, new BagCollectionType(buildingContext)),
                Map.entry(Map.class, new MapCollectionType(buildingContext))));
    }

    /** Get. */
    public CollectionType get(Class<?> collectionClass) {
        return map.get(collectionClass);
    }
}
