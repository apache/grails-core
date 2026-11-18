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
package org.apache.grails.data.neo4j.core.querytail

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.neo4j.driver.Query
import org.neo4j.driver.Record
import org.neo4j.driver.Result
import org.neo4j.driver.Session
import org.neo4j.driver.Transaction
import org.neo4j.driver.TransactionConfig
import org.neo4j.driver.TransactionWork
import org.neo4j.driver.Value

/**
 * A session of a {@link QueryTailDriver}: records every statement run on it or on a transaction it
 * begins, then hands it to the session the driver opened.
 */
@CompileStatic
@PackageScope
class QueryTailSession implements Session {

    @Delegate(deprecated = true)
    private final Session delegate

    private final QueryTailDriver driver

    QueryTailSession(Session delegate, QueryTailDriver driver) {
        this.delegate = delegate
        this.driver = driver
    }

    @Override
    Transaction beginTransaction() {
        new QueryTailTransaction(delegate.beginTransaction(), driver)
    }

    @Override
    Transaction beginTransaction(TransactionConfig config) {
        new QueryTailTransaction(delegate.beginTransaction(config), driver)
    }

    @Override
    def <T> T readTransaction(TransactionWork<T> work) {
        delegate.readTransaction(recording(work))
    }

    @Override
    def <T> T readTransaction(TransactionWork<T> work, TransactionConfig config) {
        delegate.readTransaction(recording(work), config)
    }

    @Override
    def <T> T writeTransaction(TransactionWork<T> work) {
        delegate.writeTransaction(recording(work))
    }

    @Override
    def <T> T writeTransaction(TransactionWork<T> work, TransactionConfig config) {
        delegate.writeTransaction(recording(work), config)
    }

    @Override
    Result run(String query, TransactionConfig config) {
        driver.record(query)
        delegate.run(query, config)
    }

    @Override
    Result run(String query, Map<String, Object> parameters, TransactionConfig config) {
        driver.record(query)
        delegate.run(query, parameters, config)
    }

    @Override
    Result run(Query query, TransactionConfig config) {
        driver.record(query.text())
        delegate.run(query, config)
    }

    @Override
    Result run(String query, Value parameters) {
        driver.record(query)
        delegate.run(query, parameters)
    }

    @Override
    Result run(String query, Map<String, Object> parameters) {
        driver.record(query)
        delegate.run(query, parameters)
    }

    @Override
    Result run(String query, Record parameters) {
        driver.record(query)
        delegate.run(query, parameters)
    }

    @Override
    Result run(String query) {
        driver.record(query)
        delegate.run(query)
    }

    @Override
    Result run(Query query) {
        driver.record(query.text())
        delegate.run(query)
    }

    private <T> TransactionWork<T> recording(TransactionWork<T> work) {
        { Transaction transaction -> work.execute(new QueryTailTransaction(transaction, driver)) } as TransactionWork<T>
    }
}
