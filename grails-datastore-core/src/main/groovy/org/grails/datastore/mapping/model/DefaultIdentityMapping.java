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

import org.grails.datastore.mapping.config.Property;

import static org.grails.datastore.mapping.model.MappingFactory.IDENTITY_PROPERTY;

/**
 * Default implementation of the {@link IdentityMapping} interface
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class DefaultIdentityMapping<T extends Property> extends DefaultPropertyMapping<T> implements IdentityMapping<T> {

    private final String[] identifierNames;
    private final ValueGenerator generator;
    private final boolean lazy;

    /**
     * Creates a lazy identity mapping that defers resolution of the mapped form and identifier names
     * until they are actually needed. This is necessary because during entity construction, the
     * identity property has not yet been initialized.
     *
     * @param classMapping the class mapping
     */
    public DefaultIdentityMapping(ClassMapping classMapping) {
        super(classMapping, null);
        this.generator = ValueGenerator.AUTO;
        this.identifierNames = null;
        this.lazy = true;
    }

    public DefaultIdentityMapping(ClassMapping classMapping, T mappedForm, String[] identifierNames, ValueGenerator generator) {
        super(classMapping, mappedForm);
        this.identifierNames = identifierNames;
        this.generator = generator;
        this.lazy = false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public T getMappedForm() {
        if (lazy) {
            PersistentProperty identity = getClassMapping().getEntity().getIdentity();
            if (identity != null) {
                return (T) identity.getMapping().getMappedForm();
            }
            return null;
        }
        return super.getMappedForm();
    }

    @Override
    public String[] getIdentifierName() {
        if (lazy) {
            PersistentProperty identity = getClassMapping().getEntity().getIdentity();
            if (identity != null) {
                String propertyName = identity.getMapping().getMappedForm().getName();
                if (propertyName != null) {
                    return new String[] { propertyName };
                }
            }
            return new String[] { IDENTITY_PROPERTY };
        }
        return identifierNames;
    }

    @Override
    public ValueGenerator getGenerator() {
        return generator;
    }
}
