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

package examples.mongo.tenant


import grails.gorm.multitenancy.Tenants
import grails.testing.mixin.integration.Integration
import org.grails.datastore.mapping.mongo.MongoDatastore
import org.grails.datastore.mapping.multitenancy.exceptions.TenantNotFoundException
import org.grails.datastore.mapping.multitenancy.resolvers.SystemPropertyTenantResolver
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Specification

/**
 * Created by graemerocher on 17/10/16.
 */
@Integration(applicationClass = Application)
class BookSpec extends Specification {

    @Autowired
    MongoDatastore mongoDatastore

    void "Test database per tenant"() {
        setup:
        mongoDatastore.mongoClient.listDatabaseNames().findAll { it != 'admin'&& it != 'local' && it != 'config' }.each { dbName ->
            mongoDatastore.mongoClient.getDatabase(dbName).drop()
        }

        when:"A query is executed"
        Book.list()

        then:"The tenant is not found"
        thrown TenantNotFoundException

        when:"A tenant id is specified"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test1")

        then:"A query can be executed"
        Book.list().size() == 0

        when:"We iterate over the tenants"
        List tenants = []
        Book.eachTenant { Serializable id ->
            tenants << id
        }

        then:"The ids are correct"
        tenants.size() == 2
        tenants.contains("test2")
        tenants.contains("test1")

        when:"An object is saved"
        Tenants.withCurrent{
            new Book(title: "The Stand").save(flush:true)
        }

        then:"The count is correct"
        Book.count() == 1

        when:"We switch to another tenant"
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "test2")

        then:"The count is correct"
        Book.count == 0

        cleanup:
        System.setProperty(SystemPropertyTenantResolver.PROPERTY_NAME, "")
    }
}
