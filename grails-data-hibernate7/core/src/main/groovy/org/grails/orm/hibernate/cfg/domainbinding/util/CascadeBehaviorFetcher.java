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

import java.util.Map;
import java.util.Optional;

import org.hibernate.MappingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.grails.datastore.mapping.model.types.Association;
import org.grails.datastore.mapping.model.types.Basic;
import org.grails.datastore.mapping.model.types.Embedded;
import org.grails.orm.hibernate.cfg.PropertyConfig;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty;
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty;

import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.ALL;
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.NONE;
import static org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior.SAVE_UPDATE;

/** The cascade behavior fetcher class. */
public class CascadeBehaviorFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(CascadeBehaviorFetcher.class);

    private final LogCascadeMapping logCascadeMapping;

    /** Creates a new {@link CascadeBehaviorFetcher} instance. */
    public CascadeBehaviorFetcher(LogCascadeMapping logCascadeMapping) {
        this.logCascadeMapping = logCascadeMapping;
    }

}
