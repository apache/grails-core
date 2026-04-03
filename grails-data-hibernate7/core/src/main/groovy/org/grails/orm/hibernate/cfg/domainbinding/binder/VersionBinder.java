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

import java.util.function.BiFunction;

import org.hibernate.boot.spi.MetadataBuildingContext;
import org.hibernate.engine.OptimisticLockStyle;
import org.hibernate.mapping.BasicValue;
import org.hibernate.mapping.Property;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.Table;

import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

import static org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsDomainBinder.EMPTY_PATH;

public class VersionBinder {

    private final MetadataBuildingContext metadataBuildingContext;
    private final SimpleValueBinder simpleValueBinder;
    private final PropertyBinder propertyBinder;
    private final BiFunction<MetadataBuildingContext, Table, BasicValue> basicValueFactory;

    public VersionBinder(
            MetadataBuildingContext metadataBuildingContext,
            SimpleValueBinder simpleValueBinder,
            PropertyBinder propertyBinder,
            BiFunction<MetadataBuildingContext, Table, BasicValue> basicValueFactory) {
        this.metadataBuildingContext = metadataBuildingContext;
        this.simpleValueBinder = simpleValueBinder;
        this.propertyBinder = propertyBinder;
        this.basicValueFactory = basicValueFactory;
    }

    public void bindVersion(HibernatePersistentProperty version, RootClass entity) {

        if (version != null) {

            BasicValue val = basicValueFactory.apply(metadataBuildingContext, entity.getTable());

            // set type
            simpleValueBinder.bindSimpleValue(version, null, val, EMPTY_PATH);

            // Apply version type defaults when no explicit type was configured.
            // isTypeSpecified() is always true after bindSimpleValue (Java type gets resolved),
            // so check PropertyConfig directly.
            boolean noExplicitType = version.getMappedForm() == null
                    || version.getMappedForm().getTypeName() == null;
            if (noExplicitType) {
                val.setTypeName("version".equals(version.getName()) ? "integer" : "timestamp");
            }
            Property prop = propertyBinder.bindProperty(version, val);
            prop.setLazy(false);
            val.setNullValue("undefined");
            entity.setVersion(prop);
            entity.setDeclaredVersion(prop);
            entity.setOptimisticLockStyle(OptimisticLockStyle.VERSION);
            entity.addProperty(prop);
        } else {
            entity.setOptimisticLockStyle(OptimisticLockStyle.NONE);
        }
    }
}
