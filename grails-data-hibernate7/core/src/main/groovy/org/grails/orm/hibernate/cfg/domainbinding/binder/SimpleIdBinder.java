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

import org.grails.datastore.mapping.model.DefaultPropertyMapping;
import org.grails.datastore.mapping.model.config.GormProperties;
import org.grails.orm.hibernate.cfg.Identity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateIdentityProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class SimpleIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;
    private final BasicValueIdCreator basicValueIdCreator;

    public SimpleIdBinder(
            MetadataBuildingContext metadataBuildingContext,
            BasicValueIdCreator basicValueIdCreator,
            SimpleValueBinder simpleValueBinder,
            PropertyBinder propertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueIdCreator = basicValueIdCreator;
    }

    public MetadataBuildingContext getMetadataBuildingContext() {
        return metadataBuildingContext;
    }

    public void bindSimpleId(
            @Nonnull HibernatePersistentEntity domainClass, RootClass entity, Identity mappedId, Table table) {

        Mapping result = domainClass.getMappedForm();
        boolean useSequence = result != null && result.isTablePerConcreteClass();
        // create the id value

        BasicValue id =
                basicValueIdCreator.getBasicValueId(metadataBuildingContext, table, mappedId, domainClass, useSequence);

        var identifier = domainClass.getIdentity();
        if (identifier == null) {
            var syntheticId = new HibernateIdentityProperty(
                    domainClass, domainClass.getMappingContext(), GormProperties.IDENTITY, Long.class);
            syntheticId.setMapping(new DefaultPropertyMapping<>(domainClass.getMapping(), new PropertyConfig()));
            identifier = syntheticId;
        }
        if (mappedId != null) {
            String propertyName = mappedId.getName();
            if (propertyName != null && !propertyName.equals(domainClass.getName())) {
                var namedIdentityProp = domainClass.getHibernatePropertyByName(propertyName);
                if (namedIdentityProp == null) {
                    throw new MappingException(
                            "Mapping specifies an identifier property name that doesn't exist [" + propertyName + "]");
                }
                if (!namedIdentityProp.equals(identifier)) {
                    identifier = namedIdentityProp;
                }
            }
        }

        Property idProperty = new Property();
        idProperty.setName(identifier.getName());
        idProperty.setValue(id);
        entity.setDeclaredIdentifierProperty(idProperty);
        entity.setIdentifier(id);
        // set type
        simpleValueBinder.bindSimpleValue(identifier, null, id, EMPTY_PATH);

        // bind property
        Property prop = propertyBinder.bindProperty(identifier, id);
        // set identifier property
        entity.setIdentifierProperty(prop);

        Table pkTable = id.getTable();
        pkTable.setPrimaryKey(new PrimaryKey(pkTable));
    }
}
