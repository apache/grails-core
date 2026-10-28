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
package org.apache.grails.datamapping.core.querytail

import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.CallableStatement
import java.sql.Connection
import java.sql.PreparedStatement
import java.sql.Statement

import groovy.transform.CompileStatic

/**
 * Records JDBC executions, including each entry submitted in a batch. Preparing a statement or
 * adding an entry to a batch records nothing: an unused statement or a cleared batch never ran.
 * Values bound to parameters are never inspected or retained. Installed only for query-tail capture.
 *
 * @since 9.0
 */
@CompileStatic
final class QueryTailJdbcConnection implements InvocationHandler {

    private final Connection connection
    private final QueryTailRecorder recorder

    private QueryTailJdbcConnection(Connection connection, QueryTailRecorder recorder) {
        this.connection = connection
        this.recorder = recorder
    }

    static Connection wrap(Connection connection, QueryTailRecorder recorder) {
        (Connection) Proxy.newProxyInstance(Connection.classLoader, [Connection] as Class<?>[],
                new QueryTailJdbcConnection(connection, recorder))
    }

    /** Returns the connection its provider supplied, so release uses the provider's own instance. */
    static Connection original(Connection connection) {
        if (Proxy.isProxyClass(connection.getClass())) {
            InvocationHandler handler = Proxy.getInvocationHandler(connection)
            if (handler instanceof QueryTailJdbcConnection) {
                return handler.connection
            }
        }
        connection
    }

    @Override
    Object invoke(Object proxy, Method method, Object[] args) {
        if (method.name == 'equals') {
            return proxy.is(args[0])
        }
        if (method.name == 'hashCode') {
            return System.identityHashCode(proxy)
        }
        if (method.name == 'unwrap' && ((Class<?>) args[0]).isInstance(proxy)) {
            return proxy
        }
        if (method.name == 'isWrapperFor' && ((Class<?>) args[0]).isInstance(proxy)) {
            return true
        }
        Object result = call(connection, method, args)
        if (result instanceof Statement) {
            Class<?> type = result instanceof CallableStatement ? CallableStatement :
                    result instanceof PreparedStatement ? PreparedStatement : Statement
            String sql = args != null && args.length > 0 && args[0] instanceof String ? (String) args[0] : null
            return Proxy.newProxyInstance(type.classLoader, [type] as Class<?>[],
                    new Executions((Statement) result, (Connection) proxy, recorder, sql))
        }
        result
    }

    private static Object call(Object target, Method method, Object[] args) {
        try {
            method.invoke(target, args)
        }
        catch (InvocationTargetException e) {
            throw e.cause
        }
    }

    @CompileStatic
    private static final class Executions implements InvocationHandler {

        private final Statement statement
        private final Connection connection
        private final QueryTailRecorder recorder
        private final String preparedSql
        private final List<String> batch = []

        Executions(Statement statement, Connection connection, QueryTailRecorder recorder, String preparedSql) {
            this.statement = statement
            this.connection = connection
            this.recorder = recorder
            this.preparedSql = preparedSql
        }

        @Override
        Object invoke(Object proxy, Method method, Object[] args) {
            String name = method.name
            if (name == 'equals') {
                return proxy.is(args[0])
            }
            if (name == 'hashCode') {
                return System.identityHashCode(proxy)
            }
            if (name == 'getConnection') {
                return connection
            }
            if (name == 'unwrap' && ((Class<?>) args[0]).isInstance(proxy)) {
                return proxy
            }
            if (name == 'isWrapperFor' && ((Class<?>) args[0]).isInstance(proxy)) {
                return true
            }
            if (name == 'executeBatch' || name == 'executeLargeBatch') {
                // Record attempts before JDBC runs them, as for single executions, even on failure.
                try {
                    for (String sql in batch) {
                        recorder.record(sql)
                    }
                    return call(statement, method, args)
                }
                finally {
                    batch.clear()
                }
            }
            String sql = args != null && args.length > 0 && args[0] instanceof String ? (String) args[0] : preparedSql
            if (name == 'execute' || name == 'executeQuery' || name == 'executeUpdate' || name == 'executeLargeUpdate') {
                recorder.record(sql)
            }
            Object result = call(statement, method, args)
            if (name == 'addBatch') {
                batch.add(sql)
            }
            else if (name == 'clearBatch' || name == 'close') {
                batch.clear()
            }
            result
        }
    }
}
