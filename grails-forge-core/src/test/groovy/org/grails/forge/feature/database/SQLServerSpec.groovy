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

package org.grails.forge.feature.database

import org.grails.forge.ApplicationContextSpec
import org.grails.forge.BuildBuilder
import org.grails.forge.application.generator.GeneratorContext

class SQLServerSpec extends ApplicationContextSpec {

    void 'test gradle sqlserver feature'() {
        when:
        String template = new BuildBuilder(beanContext)
                .features(['sqlserver'])
                .render()

        then:
        template.contains('runtimeOnly "com.microsoft.sqlserver:mssql-jdbc"')
    }

    void "test there can only be one of DatabaseDriverFeature"() {
        when:
        getFeatures(beanContext.getBeansOfType(DatabaseDriverFeature)*.name)

        then:
        def ex = thrown(IllegalArgumentException)
        ex.message.contains("There can only be one of the following features selected")
    }

    void "test config"() {
        when:
        GeneratorContext ctx = buildGeneratorContext(["gorm-hibernate5", "sqlserver"])

        then:
        ctx.getConfiguration().get("dataSource.driverClassName") == 'com.microsoft.sqlserver.jdbc.SQLServerDriver'
        ctx.getConfiguration().get("dataSource.username") == 'sa'
        ctx.getConfiguration().get("dataSource.password") == ''
        ctx.getConfiguration().get("environments.development.dataSource.url") == 'jdbc:sqlserver://localhost:1433;databaseName=devDb'
        ctx.getConfiguration().get("environments.test.dataSource.url") == 'jdbc:sqlserver://localhost:1433;databaseName=testDb'
        ctx.getConfiguration().get("environments.production.dataSource.url") == 'jdbc:sqlserver://localhost:1433;databaseName=prodDb'
    }

}
