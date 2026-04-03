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

import jakarta.annotation.Nonnull;

import org.hibernate.MappingException;
import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.PrimaryKey;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateSimpleIdentityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueCreator;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

/** The simple id binder class. */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class SimpleIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final BasicValueCreator basicValueCreator;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;

    public SimpleIdBinder(
            MetadataBuildingContext metadataBuildingContext,
            BasicValueCreator basicValueCreator,
            SimpleValueBinder simpleValueBinder,
            PropertyBinder propertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.basicValueCreator = basicValueCreator;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
    }

    public MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext;
    }

    public void bindSimpleId(@Nonnull HibernatePersistentEntity domainClass) {
        if (domainClass.getIdentityProperty() instanceof HibernateSimpleIdentityProperty simpleIdentityProperty) {
            RootClass rootClass = domainClass.getRootClass();
            BasicValue id = basicValueCreator.bindBasicValue(simpleIdentityProperty);

            var identifier = basicValueCreator.resolveIdentifierProperty(domainClass, simpleIdentityProperty);

            Property idProperty = new Property();
            idProperty.setName(identifier.getName());
            idProperty.setValue(id);
            rootClass.setDeclaredIdentifierProperty(idProperty);
            rootClass.setIdentifier(id);
            // set type
            simpleValueBinder.bindSimpleValue(identifier, null, id, EMPTY_PATH);

            // bind property
            Property prop = propertyBinder.bindProperty(identifier, id);
            // set identifier property
            rootClass.setIdentifierProperty(prop);

            Table pkTable = id.getTable();
            pkTable.setPrimaryKey(new PrimaryKey(pkTable));
            return;
        }
        throw new MappingException("Invalid composite id binding for entity [" + domainClass.getName() + "]");
    }
}
