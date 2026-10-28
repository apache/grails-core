/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package liquibase.ext.hibernate.database

import groovy.transform.CompileStatic

import org.junit.Test

import static org.junit.Assert.assertFalse

/**
 * {@link HibernateDatabase} has a public {@code getProperty(String)} that returns a connection property.
 * A Groovy class would take it for the {@code GroovyObject} hook behind every dynamic property access,
 * so the whole family is compiled without {@code GroovyObject}.
 */
@CompileStatic
class HibernateDatabasePojoTest {

    @Test
    void everyHibernateDatabaseIsAPlainObject() {
        [HibernateDatabase, HibernateClassicDatabase, HibernateEjb3Database, JpaPersistenceDatabase,
         HibernateSpringBeanDatabase, HibernateSpringPackageDatabase].each { Class<?> type ->
            assertFalse("${type.name} implements GroovyObject", GroovyObject.isAssignableFrom(type))
        }
    }

}
