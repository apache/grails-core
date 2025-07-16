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
package org.grails.datastore.gorm.neo4j.mapping.config;

import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.datastore.mapping.model.PropertyMapping;
import org.grails.datastore.mapping.model.types.ToMany;

/**
 * Represents an association that is dynamic and is created at runtime
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class DynamicToManyAssociation extends ToMany implements DynamicAssociation{

    public DynamicToManyAssociation(PersistentEntity owner, MappingContext context, String name, PersistentEntity associatedType) {
        super(owner, context, name, associatedType.getJavaClass());
        setAssociatedEntity(associatedType);
        setOwningSide(true);
    }

    @Override
    public PropertyMapping getMapping() {
        return null;
    }
}
