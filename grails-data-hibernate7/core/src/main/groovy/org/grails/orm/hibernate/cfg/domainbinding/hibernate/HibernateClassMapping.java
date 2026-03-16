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

import org.grails.datastore.mapping.model.AbstractClassMapping;
import org.grails.datastore.mapping.model.MappingContext;
import org.grails.datastore.mapping.model.PersistentEntity;
import org.grails.orm.hibernate.cfg.Mapping;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior;

/**
 * A {@link org.grails.datastore.mapping.model.ClassMapping} implementation for Hibernate
 *
 * @author Graeme Rocher
 * @since 5.0
 */
public class HibernateClassMapping extends AbstractClassMapping<Mapping> {
    private final Mapping mappedForm;

    public HibernateClassMapping(PersistentEntity entity, MappingContext context) {
        super(entity, context);
        this.mappedForm = (Mapping) context.getMappingFactory().createMappedForm(entity);
        for (PropertyConfig propConf : mappedForm.getPropertyConfigs().values()) {
            if (propConf != null && propConf.getCascade() != null) {
                propConf.setExplicitSaveUpdateCascade(CascadeBehavior.isSaveUpdate(propConf.getCascade()));
            }
        }
    }

    @Override
    public Mapping getMappedForm() {
        return mappedForm;
    }
}
