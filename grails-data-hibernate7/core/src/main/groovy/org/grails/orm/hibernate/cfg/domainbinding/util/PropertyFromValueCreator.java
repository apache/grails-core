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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import org.hibernate.mapping.ManyToOne;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateEnumProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToOneProperty;

public class PropertyFromValueCreator {

    private final PropertyBinder propertyBinder;

    public PropertyFromValueCreator() {
        this.propertyBinder = new PropertyBinder();
    }

    protected PropertyFromValueCreator(PropertyBinder propertyBinder) {
        this.propertyBinder = propertyBinder;
    }

    public Property createProperty(Value value, HibernatePersistentProperty grailsProperty) {
        // set type
        if (!(grailsProperty instanceof HibernateEnumProperty)) {
            value.setTypeUsingReflection(grailsProperty.getOwnerClassName(), grailsProperty.getName());
        }

        if (value.getTable() != null && !referencesCompositeIdentifier(value, grailsProperty)) {
            value.createForeignKey();
        }

        return propertyBinder.bindProperty(grailsProperty, value);
    }

    /**
     * A many-to-one to a composite identifier gets its foreign key from the composite identifier
     * binder, which pairs each column with the identifier column it references once that identifier
     * is bound. Hibernate's own foreign key would pair the columns by position instead.
     */
    private static boolean referencesCompositeIdentifier(Value value, HibernatePersistentProperty grailsProperty) {
        if (!(value instanceof ManyToOne) || !(grailsProperty instanceof HibernateToOneProperty toOne)) {
            return false;
        }
        GrailsHibernatePersistentEntity associatedEntity = toOne.getHibernateAssociatedEntity();
        return associatedEntity != null && associatedEntity.getHibernateCompositeIdentity().isPresent();
    }
}
