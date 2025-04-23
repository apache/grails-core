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
package org.grails.datastore.mapping.model;

import java.beans.PropertyDescriptor;

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.reflect.EntityReflector;
import org.grails.datastore.mapping.reflect.NameUtils;

/**
 * Abstract implementation of the PersistentProperty interface that uses the
 * PropertyDescriptor instance to establish name and type.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@SuppressWarnings("rawtypes")
public abstract class AbstractPersistentProperty<T extends Property> implements PersistentProperty<T> {
    protected final PersistentEntity owner;
    protected final MappingContext context;
    protected final String name;
    protected final Class type;
    protected Boolean inherited;
    private EntityReflector.PropertyReader reader;
    private EntityReflector.PropertyWriter writer;

    public AbstractPersistentProperty(PersistentEntity owner, MappingContext context, PropertyDescriptor descriptor) {
        this(owner, context, descriptor.getName(), descriptor.getPropertyType());
    }

    public AbstractPersistentProperty(PersistentEntity owner, MappingContext context, String name, Class type) {
        this.owner = owner;
        this.context = context;
        this.name = name;
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public String getCapitilizedName() {
        return NameUtils.capitalize(getName());
    }

    public Class getType() {
        return type;
    }

    public PersistentEntity getOwner() {
        return owner;
    }

    @Override
    public String toString() {
        String mappingType = getClass().getName();
        if (mappingType.contains("$")) {
            mappingType = getClass().getSuperclass().getName();
        }
        return getName() + ":" + getType().getName() + " (" + mappingType + ")";
    }

    public boolean isNullable() {
        final T mappedForm = getMapping().getMappedForm();
        return mappedForm != null && mappedForm.isNullable();
    }

    @Override
    public boolean isInherited() {
        if (inherited == null) {

            if (owner.isRoot()) {
                inherited = false;
            } else {
                PersistentEntity parentEntity = owner.getParentEntity();
                boolean foundInParent = false;
                while (parentEntity != null) {
                    final PersistentProperty p = parentEntity.getPropertyByName(name);
                    if (p != null) {
                        foundInParent = true;
                        break;
                    }
                    parentEntity = parentEntity.getParentEntity();
                }

                inherited = foundInParent;
            }
        }

        return inherited;
    }

    @Override
    public EntityReflector.PropertyReader getReader() {
        if (reader == null) {
            reader = getOwner().getReflector().getPropertyReader(getName());
        }
        return reader;
    }

    @Override
    public EntityReflector.PropertyWriter getWriter() {
        if (writer == null) {
            writer = getOwner().getReflector().getPropertyWriter(getName());
        }
        return writer;
    }
}
