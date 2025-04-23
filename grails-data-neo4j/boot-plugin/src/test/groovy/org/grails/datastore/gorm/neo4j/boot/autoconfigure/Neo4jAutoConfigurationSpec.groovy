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

package org.grails.datastore.gorm.neo4j.boot.autoconfigure

import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.neo4j.config.Settings
import org.springframework.boot.autoconfigure.AutoConfigurationPackages
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import spock.lang.Specification

/**
 * Created by graemerocher on 23/11/15.
 */
class Neo4jDbGormAutoConfigurationSpec extends Specification{

    protected AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();

    void cleanup() {
        context.close()
    }

    void setup() {

        System.setProperty(Settings.SETTING_NEO4J_TYPE, Settings.DATABASE_TYPE_EMBEDDED)
        System.setProperty(Settings.SETTING_NEO4J_EMBEDDED_EPHEMERAL, "true")
        AutoConfigurationPackages.register(context, Neo4jDbGormAutoConfigurationSpec.package.name)
        this.context.register( TestConfiguration, PropertyPlaceholderAutoConfiguration.class, );

    }


    void 'Test that GORM is correctly configured'() {
        when:"The context is refreshed"
        context.refresh()

        then:"GORM queries work"
        Person.count() == 0
    }

    @Configuration
    @EnableAutoConfiguration
    @Import(Neo4jAutoConfiguration)
    static class TestConfiguration {
    }

}


@Entity
class Person {
    String firstName
    String lastName
    Integer age = 18
}


