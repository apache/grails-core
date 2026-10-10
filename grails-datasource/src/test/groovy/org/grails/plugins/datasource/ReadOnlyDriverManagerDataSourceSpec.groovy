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
package org.grails.plugins.datasource

import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.DriverPropertyInfo
import java.sql.SQLException
import java.util.logging.Logger

import org.springframework.jdbc.datasource.DriverManagerDataSource
import spock.lang.Specification

class ReadOnlyDriverManagerDataSourceSpec extends Specification {

    void 'connections obtained from the data source are switched to read only'() {
        given:
        RecordingDriver driver = new RecordingDriver()
        DriverManager.registerDriver(driver)
        ReadOnlyDriverManagerDataSource dataSource = new ReadOnlyDriverManagerDataSource()
        dataSource.url = RecordingDriver.URL
        dataSource.username = 'sa'
        dataSource.password = ''

        when:
        Connection connection = dataSource.getConnection()
        Connection withCredentials = dataSource.getConnection('sa', '')

        then:
        dataSource instanceof DriverManagerDataSource
        connection.isReadOnly()
        withCredentials.isReadOnly()
        driver.connections.size() == 2
        driver.connections*.readOnly == [true, true]
        driver.properties*.getProperty('user') == ['sa', 'sa']
        !new DriverManagerDataSource(RecordingDriver.URL, 'sa', '').getConnection().isReadOnly()

        cleanup:
        DriverManager.deregisterDriver(driver)
    }

}

class RecordingDriver implements Driver {

    static final String URL = 'jdbc:recording:readonly'

    List<Connection> connections = []
    List<Properties> properties = []

    @Override
    Connection connect(String url, Properties info) throws SQLException {
        if (!acceptsURL(url)) {
            return null
        }
        boolean[] readOnly = [false]
        Connection connection = (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().classLoader, [Connection] as Class[], { Object proxy, java.lang.reflect.Method method, Object[] args ->
            switch (method.name) {
                case 'setReadOnly': readOnly[0] = (boolean) args[0]; return null
                case 'isReadOnly': return readOnly[0]
                case 'close': return null
                case 'hashCode': return System.identityHashCode(proxy)
                case 'equals': return proxy.is(args[0])
                case 'toString': return 'recording-connection'
                default: throw new UnsupportedOperationException(method.name)
            }
        } as java.lang.reflect.InvocationHandler)
        connections << connection
        properties << info
        connection
    }

    @Override
    boolean acceptsURL(String url) throws SQLException {
        url == URL
    }

    @Override
    DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        new DriverPropertyInfo[0]
    }

    @Override
    int getMajorVersion() {
        1
    }

    @Override
    int getMinorVersion() {
        0
    }

    @Override
    boolean jdbcCompliant() {
        false
    }

    @Override
    Logger getParentLogger() {
        Logger.getLogger('recording')
    }

}
