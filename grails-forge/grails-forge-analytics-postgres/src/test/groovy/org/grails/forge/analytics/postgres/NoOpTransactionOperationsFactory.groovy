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

package org.grails.forge.analytics.postgres

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.data.connection.ConnectionStatus
import io.micronaut.transaction.TransactionCallback
import io.micronaut.transaction.TransactionDefinition
import io.micronaut.transaction.TransactionOperations
import io.micronaut.transaction.TransactionStatus

import jakarta.inject.Singleton

import java.util.Optional

@Factory
@Requires(env = 'test')
class NoOpTransactionOperationsFactory {

    @Singleton
    TransactionOperations<Object> transactionOperations() {
        new TransactionOperations<Object>() {
            @Override
            Object getConnection() {
                null
            }

            @Override
            boolean hasConnection() {
                false
            }

            @Override
            Optional findTransactionStatus() {
                Optional.empty()
            }

            @Override
            <R> R execute(TransactionDefinition definition, TransactionCallback<Object, R> callback) {
                callback.call(new TransactionStatus<Object>() {
                    @Override
                    Object getTransaction() {
                        null
                    }

                    @Override
                    ConnectionStatus<Object> getConnectionStatus() {
                        null
                    }

                    @Override
                    boolean isNewTransaction() {
                        false
                    }

                    @Override
                    void setRollbackOnly() {
                    }

                    @Override
                    boolean isRollbackOnly() {
                        false
                    }

                    @Override
                    boolean isCompleted() {
                        false
                    }

                    @Override
                    TransactionDefinition getTransactionDefinition() {
                        definition
                    }
                })
            }
        }
    }
}
