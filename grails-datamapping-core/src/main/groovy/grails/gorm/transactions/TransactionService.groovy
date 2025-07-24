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

package grails.gorm.transactions

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType

import org.springframework.transaction.TransactionDefinition

/**
 * A GORM service that simplifies the execution of transactions
 *
 * @author Graeme Rocher
 * @since 6.1
 */
interface TransactionService {

    /**
     * Executes the given callable within the context of a transaction with the default attributes
     *
     * @param callable The callable
     * @return The result
     */
    public <T> T withTransaction(@ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction that is automatically rolled back with the default attributes
     *
     * @param callable The callable
     * @return The result
     */
    public <T> T withRollback(@ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)
    /**
     * Executes the given callable within the context of a new transaction with the default attributes
     *
     * @param callable The callable
     * @return The result
     */
    public <T> T withNewTransaction(@ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction with the given definition
     *
     * @param definition The transaction definition
     * @param callable The callable The callable
     * @return The result of the callable
     */
    public <T> T withTransaction(TransactionDefinition definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction with the given definition
     *
     * @param definition The transaction definition as a map
     * @param callable The callable The callable
     * @return The result of the callable
     */
    public <T> T withTransaction(Map definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)

    /**
     * Executes the given callable within the context of a transaction that is automatically rolled back with the default attributes
     *
     * @param definition The transaction definition
     * @param callable The callable
     * @return The result
     */
    public <T> T withRollback(TransactionDefinition definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)
    /**
     * Executes the given callable within the context of a new transaction with the default attributes
     *
     * @param definition The transaction definition
     * @param callable The callable
     * @return The result
     */
    public <T> T withNewTransaction(TransactionDefinition definition, @ClosureParams(value=SimpleType.class, options="org.springframework.transaction.TransactionStatus") Closure<T> callable)
}
