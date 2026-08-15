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
package org.grails.datastore.gorm.jdbc.connections

import javax.sql.DataSource

import org.springframework.jdbc.datasource.DelegatingDataSource
import spock.lang.Specification

class DataSourceConnectionSourceSpec extends Specification {

    static interface CloseableDataSource extends DataSource {
        void close()
    }

    void "close invokes a public close() method on the underlying DataSource when present"() {
        given:
        def dataSource = Mock(CloseableDataSource)
        def source = new DataSourceConnectionSource('test', dataSource, new DataSourceSettings())

        when:
        source.close()

        then:
        1 * dataSource.close()
    }

    void "close unwraps a DelegatingDataSource to find a close() method on the target"() {
        given:
        def closeable = Mock(CloseableDataSource)
        def delegating = new DelegatingDataSource(closeable)
        def source = new DataSourceConnectionSource('test', delegating, new DataSourceSettings())

        when:
        source.close()

        then:
        1 * closeable.close()
    }

    void "close does not fail when the DataSource has no close() method"() {
        given:
        def dataSource = Mock(DataSource)
        def source = new DataSourceConnectionSource('test', dataSource, new DataSourceSettings())

        when:
        source.close()

        then:
        noExceptionThrown()
    }

    void "close swallows exceptions thrown by the underlying close() method"() {
        given:
        def dataSource = Mock(CloseableDataSource) {
            close() >> { throw new RuntimeException('boom') }
        }
        def source = new DataSourceConnectionSource('test', dataSource, new DataSourceSettings())

        when:
        source.close()

        then:
        noExceptionThrown()
    }

    void "getName, getSource and getSettings expose the constructor arguments"() {
        given:
        def dataSource = Mock(DataSource)
        def settings = new DataSourceSettings()

        when:
        def source = new DataSourceConnectionSource('test', dataSource, settings)

        then:
        source.name == 'test'
        source.source.is(dataSource)
        source.settings.is(settings)
    }
}
