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
import org.neo4j.driver.Transaction
import org.neo4j.driver.Value

/**
 * A transaction of a {@link QueryTailSession}: records every statement run in it, then hands it to
 * the transaction the session began.
 */
@CompileStatic
@PackageScope
class QueryTailTransaction implements Transaction {

    @Delegate(deprecated = true)
    private final Transaction delegate

    private final QueryTailDriver driver

    QueryTailTransaction(Transaction delegate, QueryTailDriver driver) {
        this.delegate = delegate
        this.driver = driver
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
}
