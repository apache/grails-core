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
package org.grails.orm.hibernate;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;

import org.springframework.orm.hibernate5.SessionHolder;

import org.grails.orm.hibernate.support.TransactionResources;

/**
 * Strategy applied when an outer session exists but no Spring transaction synchronization is active.
 *
 * <p>Primes a disconnected outer session so that {@code txResources.getResource(dataSource)} captures
 * its {@code ConnectionHolder} before the new session begins, preventing outer session lazy loading
 * inside {@code withNewSession} from borrowing the inner transaction's connection.</p>
 */
class DefaultOuterSessionStrategy extends AbstractOuterSessionLifecycleStrategy {

    DefaultOuterSessionStrategy(
            SessionFactory sessionFactory,
            DataSource dataSource,
            TransactionResources txResources,
            SessionHolder previousHolder,
            boolean lazyConnection) {
        super(sessionFactory, dataSource, txResources, previousHolder, lazyConnection);
    }

    @Override
    public void prepare() {
        super.prepare();
    }
}
