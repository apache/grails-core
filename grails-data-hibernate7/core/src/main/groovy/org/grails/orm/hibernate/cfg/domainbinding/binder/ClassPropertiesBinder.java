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
package org.grails.orm.hibernate.cfg.domainbinding.binder;

import org.jspecify.annotations.NonNull;

import org.hibernate.MappingException;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.Table;
import org.hibernate.mapping.Value;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator;

/**
 * Binds the properties of a Grails domain class to the Hibernate meta-model.
 *
 * @since 8.0
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class ClassPropertiesBinder {

    private final GrailsPropertyBinder grailsPropertyBinder;
    private final PropertyFromValueCreator propertyFromValueCreator;
    private final NaturalIdentifierBinder naturalIdentifierBinder;

    public ClassPropertiesBinder(
            GrailsPropertyBinder grailsPropertyBinder,
            PropertyFromValueCreator propertyFromValueCreator,
            NaturalIdentifierBinder naturalIdentifierBinder) {
        this.grailsPropertyBinder = grailsPropertyBinder;
        this.propertyFromValueCreator = propertyFromValueCreator;
        this.naturalIdentifierBinder = naturalIdentifierBinder;
    }

    public ClassPropertiesBinder(
            GrailsPropertyBinder grailsPropertyBinder, PropertyFromValueCreator propertyFromValueCreator) {
        this(grailsPropertyBinder, propertyFromValueCreator, new NaturalIdentifierBinder());
    }

    public void bindClassProperties(HibernatePersistentEntity hibernatePersistentEntity) {
        PersistentClass persistentClass = hibernatePersistentEntity.getPersistentClass();
        getTable(persistentClass).setComment(hibernatePersistentEntity.getComment());
        for (HibernatePersistentProperty currentGrailsProp :
                hibernatePersistentEntity.getPersistentPropertiesToBind()) {
            Value value = grailsPropertyBinder.bindProperty(currentGrailsProp, null, GrailsDomainBinder.EMPTY_PATH);
            persistentClass.addProperty(propertyFromValueCreator.createProperty(value, currentGrailsProp));
        }

        naturalIdentifierBinder.bindNaturalIdentifier(hibernatePersistentEntity, persistentClass);
    }

    @NonNull
    private Table getTable(PersistentClass persistentClass) {
        if (persistentClass.getTable() == null) {
            throw new MappingException("Persistent class [" + persistentClass.getEntityName() +
                    "] does not have a table associated with it");
        }
        return persistentClass.getTable();
    }
}
