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

import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateAssociation;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty;

@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class DefaultColumnNameFetcher {

    private static final String FOREIGN_KEY_SUFFIX = "_id";
    private static final String UNDERSCORE = "_";

    private final PersistentEntityNamingStrategy namingStrategyWrapper;
    private final BackticksRemover backticksRemover;

    public DefaultColumnNameFetcher(PersistentEntityNamingStrategy namingStrategyWrapper) {
        this.namingStrategyWrapper = namingStrategyWrapper;
        this.backticksRemover = new BackticksRemover();
    }

    public DefaultColumnNameFetcher(
            PersistentEntityNamingStrategy namingStrategyWrapper, BackticksRemover backticksRemover) {
        this.namingStrategyWrapper = namingStrategyWrapper;
        this.backticksRemover = backticksRemover;
    }

    public String getDefaultColumnName(HibernatePersistentProperty property) {

        String columnName = namingStrategyWrapper.resolveColumnName(property.getName());
        if (property instanceof HibernateAssociation association) {
            boolean isBasic = property instanceof HibernateToManyProperty toMany && toMany.isBasic();
            if (isBasic && (property.getMappedForm()).getType() != null) {
                return columnName;
            }

            if (isBasic) {
                return namingStrategyWrapper.resolveForeignKeyForPropertyDomainClass(property);
            }

            if (property instanceof HibernateManyToManyProperty) {
                return namingStrategyWrapper.resolveForeignKeyForPropertyDomainClass(property);
            }

            if (!association.isBidirectional() && association instanceof HibernateOneToManyProperty) {
                String prefix = namingStrategyWrapper.resolveTableName(
                        property.getOwner().getRootEntity().getJavaClass().getSimpleName());
                return backticksRemover.apply(prefix)
                        + UNDERSCORE
                        + backticksRemover.apply(columnName)
                        + FOREIGN_KEY_SUFFIX;
            }

            if (property.isInherited() && property.isBidirectionalManyToOne()) {
                return namingStrategyWrapper.resolveColumnName(property.getOwner()
                                .getRootEntity()
                                .getJavaClass()
                                .getSimpleName())
                        + '_'
                        + columnName
                        + FOREIGN_KEY_SUFFIX;
            }

            return columnName + FOREIGN_KEY_SUFFIX;
        }

        return columnName;
    }
}
