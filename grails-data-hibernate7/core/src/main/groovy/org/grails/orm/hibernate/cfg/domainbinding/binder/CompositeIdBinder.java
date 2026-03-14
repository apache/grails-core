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
import org.hibernate.mapping.Component;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.CompositeIdentity;
import org.grails.orm.hibernate.cfg.GrailsHibernateUtil;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class CompositeIdBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final ComponentUpdater componentUpdater;
    private final GrailsPropertyBinder grailsPropertyBinder;

    public CompositeIdBinder(
            MetadataBuildingContext metadataBuildingContext,
            ComponentUpdater componentUpdater,
            GrailsPropertyBinder grailsPropertyBinder) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.componentUpdater = componentUpdater;
        this.grailsPropertyBinder = grailsPropertyBinder;
    }

    public void bindCompositeId(
            @Nonnull HibernatePersistentEntity hibernatePersistentEntity, RootClass root, CompositeIdentity compositeIdentity) {
        hibernatePersistentEntity.setPersistentClass(root);
        Component id = new Component(metadataBuildingContext, root);
        id.setNullValue("undefined");
        root.setIdentifier(id);
        root.setIdentifierMapper(id);
        root.setEmbeddedIdentifier(true);
        id.setComponentClassName(hibernatePersistentEntity.getName());
        id.setKey(true);
        id.setEmbedded(true);

        String path = GrailsHibernateUtil.qualify(root.getEntityName(), "id");

        id.setRoleName(path);

        HibernatePersistentProperty[] composite = compositeIdentity != null
                ? compositeIdentity.getHibernateProperties(hibernatePersistentEntity)
                : hibernatePersistentEntity.getCompositeIdentity();

        if (composite == null || composite.length == 0) {
            throw new MappingException("No composite identifier properties found for class [" + hibernatePersistentEntity.getName() + "]");
        }
        PersistentClass persistentClass = hibernatePersistentEntity.getPersistentClass();
        Table table = persistentClass.getTable();
        HibernatePersistentProperty identifierProp = hibernatePersistentEntity.getIdentity();
        for (HibernatePersistentProperty property : composite) {
            var value = grailsPropertyBinder.bindProperty(persistentClass, table, "", identifierProp, property);
            componentUpdater.updateComponent(id, identifierProp, property, value);
        }
    }
}
