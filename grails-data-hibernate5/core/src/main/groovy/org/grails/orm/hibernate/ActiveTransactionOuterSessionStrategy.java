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
 * Strategy applied when the outer session is bound to an active write-bearing physical transaction
 * (such as {@code PROPAGATION_REQUIRED} or {@code PROPAGATION_MANDATORY}).
 *
 * <p>Invariant: Never disconnect an active outer transaction during beforeCompletion or suspension,
 * as doing so drops uncommitted entity dirty checks and pending flushes.</p>
 */
class ActiveTransactionOuterSessionStrategy extends AbstractOuterSessionLifecycleStrategy {

    ActiveTransactionOuterSessionStrategy(
            SessionFactory sessionFactory,
            DataSource dataSource,
            TransactionResources txResources,
            SessionHolder previousHolder,
            boolean lazyConnection) {
        super(sessionFactory, dataSource, txResources, previousHolder, lazyConnection);
    }

    @Override
    public void prepare() {
        this.wasPhysicallyConnectedAtPrepare = true;
        // No preparation needed: the active transaction already manages its physical connection.
        // We explicitly do NOT disconnect or register disconnect synchronizations here.
    }
}
