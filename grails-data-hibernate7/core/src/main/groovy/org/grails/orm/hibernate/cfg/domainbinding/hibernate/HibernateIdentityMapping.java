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

import org.grails.datastore.mapping.config.Property;
import org.grails.datastore.mapping.model.ClassMapping;
import org.grails.datastore.mapping.model.IdentityMapping;
import org.grails.datastore.mapping.model.ValueGenerator;
import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.Identity;

/**
 * {@link IdentityMapping} implementation for Hibernate that resolves identifier names from {@link
 * Identity} and {@link CompositeIdentity} mapped forms.
 */
public class HibernateIdentityMapping implements IdentityMapping<Property> {

    private static final String[] DEFAULT_IDENTITY_MAPPING = new String[] {"id"};

    private final Object identity;
    private final ValueGenerator generator;
    private final ClassMapping classMapping;

    /**
     * Constructs a HibernateIdentityMapping.
     *
     * @param identity the identity mapped form ({@link Identity} or {@link CompositeIdentity})
     * @param generator the resolved {@link ValueGenerator}
     * @param classMapping the owning {@link ClassMapping}
     */
    public HibernateIdentityMapping(Object identity, ValueGenerator generator, ClassMapping classMapping) {
        this.identity = identity;
        this.generator = generator;
        this.classMapping = classMapping;
    }

    @Override
    public String[] getIdentifierName() {
        if (identity instanceof Identity) {
            final String name = ((Identity) identity).getName();
            if (name != null) {
                return new String[] {name};
            } else {
                return DEFAULT_IDENTITY_MAPPING;
            }
        } else if (identity instanceof CompositeIdentity) {
            return ((CompositeIdentity) identity).getPropertyNames();
        }
        return DEFAULT_IDENTITY_MAPPING;
    }

    @Override
    public ValueGenerator getGenerator() {
        return generator;
    }

    @Override
    public ClassMapping getClassMapping() {
        return classMapping;
    }

    @Override
    public Property getMappedForm() {
        return (Property) identity;
    }
}
