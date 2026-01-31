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

    public DefaultIdentityMapping(ClassMapping classMapping, T mappedForm) {
        this(classMapping, mappedForm, ValueGenerator.AUTO);
    }

    public DefaultIdentityMapping(ClassMapping classMapping, T mappedForm, ValueGenerator generator) {
        super(classMapping, mappedForm);
        this.generator = generator;
        PersistentProperty identity = classMapping.getEntity().getIdentity();
        String propertyName = identity != null ? identity.getMapping().getMappedForm().getName() : null;
        if (propertyName != null) {
            this.identifierNames = new String[] { propertyName };
        }
        else {
            this.identifierNames = new String[] { IDENTITY_PROPERTY };
        }
    }

    public DefaultIdentityMapping(ClassMapping classMapping, T mappedForm, String[] identifierNames, ValueGenerator generator) {
        super(classMapping, mappedForm);
        this.identifierNames = identifierNames;
        this.generator = generator;
    }

    @Override
    public String[] getIdentifierName() {
        return identifierNames;
    }

    @Override
    public ValueGenerator getGenerator() {
        return generator;
    }
}
