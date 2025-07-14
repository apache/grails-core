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
package org.grails.datastore.mapping.model.types;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;

import java.beans.PropertyDescriptor;

/**
 * Models an embedded component
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class Embedded<T extends Property> extends ToOne<T> {

    public Embedded(PersistentEntity owner, MappingContext context, PropertyDescriptor descriptor) {
        super(owner, context, descriptor);
    }

    public Embedded(PersistentEntity owner, MappingContext context, String name, @SuppressWarnings("rawtypes") Class type) {
        super(owner, context, name, type);
    }

    @Override
    public boolean isOwningSide() {
        return true; // embedded instances are always owned
    }
}
