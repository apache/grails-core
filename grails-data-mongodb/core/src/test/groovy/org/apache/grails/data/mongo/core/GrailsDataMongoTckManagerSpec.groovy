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
package org.apache.grails.data.mongo.core

import org.bson.Document

import org.apache.grails.data.testing.tck.domains.Book
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingProduct
import org.apache.grails.data.testing.tck.domains.DataServiceRoutingProductService
import org.apache.grails.data.testing.tck.tests.DataServiceConnectionRoutingSpec
import spock.lang.Specification

class GrailsDataMongoTckManagerSpec extends Specification {

    void 'cleanup removes the documents but keeps the collections so the next feature does not recreate them'() {
        given:
        def manager = new GrailsDataMongoTckManager()
        manager.registerDomainClasses(Book)
        manager.setupSpec()

        when:
        manager.setup(DataServiceConnectionRoutingSpec)
        new Book(title: 'The Stand', author: 'Stephen King').save(flush: true, failOnError: true)
        def collectionNames = manager.mongoClient.getDatabase('test').listCollectionNames().toList()

        then:
        Book.count() == 1
        collectionNames.contains('book')

        when:
        manager.cleanup()
        manager.setup(DataServiceConnectionRoutingSpec)
        def database = manager.mongoClient.getDatabase('test')

        then:
        Book.count() == 0
        database.listCollectionNames().toList().containsAll(collectionNames)
        database.getCollection('book').countDocuments(new Document()) == 0

        cleanup:
        manager.cleanup()
        manager.cleanupSpec()
    }

    void 'cleanup drops collections that do not support deletes'() {
        given:
        def manager = new GrailsDataMongoTckManager()
        manager.registerDomainClasses(Book)
        manager.setupSpec()
        manager.setup(DataServiceConnectionRoutingSpec)
        def database = manager.mongoClient.getDatabase('test')
        database.createView('bookView', 'book', [new Document('$match', new Document())])
        new Book(title: 'The Stand', author: 'Stephen King').save(flush: true, failOnError: true)

        when:
        manager.cleanup()
        manager.setup(DataServiceConnectionRoutingSpec)
        database = manager.mongoClient.getDatabase('test')
        def collectionNames = database.listCollectionNames().toList()

        then:
        !collectionNames.contains('bookView')
        collectionNames.contains('book')
        Book.count() == 0

        cleanup:
        manager.cleanup()
        manager.cleanupSpec()
    }

    void 'the mongod container can open more files than the Docker default allows'() {
        given:
        def manager = new GrailsDataMongoTckManager()
        manager.setupSpec()

        expect:
        manager.mongoDBContainer.execInContainer('sh', '-c', 'ulimit -n').stdout.trim().toLong() > 1024L

        cleanup:
        manager.cleanupSpec()
    }

    void 'cleanup closes the primary datastore so repeated setup stays healthy'() {
        given:
        def manager = new GrailsDataMongoTckManager()
        manager.setupSpec()

        when:
        manager.setup(DataServiceConnectionRoutingSpec)
        manager.setupMultiDataSource(DataServiceRoutingProduct)

        and:
        def productService = manager.getServiceForConnection(
                DataServiceRoutingProductService,
                'secondary'
        ) as DataServiceRoutingProductService
        def saved = productService.save(
                new DataServiceRoutingProduct(name: 'product-0', amount: 1)
        )

        then:
        saved != null
        saved.id != null
        manager.mongoDatastore != null

        when:
        manager.cleanupMultiDataSource()
        manager.cleanup()

        then:
        manager.mongoDatastore == null
        manager.mongoClient == null
        manager.grailsApplication == null
        manager.mappingContext == null

        when:
        manager.setup(DataServiceConnectionRoutingSpec)
        manager.setupMultiDataSource(DataServiceRoutingProduct)

        and:
        productService = manager.getServiceForConnection(
                DataServiceRoutingProductService,
                'secondary'
        ) as DataServiceRoutingProductService
        saved = productService.save(
                new DataServiceRoutingProduct(name: 'product-1', amount: 1)
        )

        then:
        saved != null
        saved.id != null
        manager.mongoDatastore != null

        when:
        manager.cleanupMultiDataSource()
        manager.cleanup()

        then:
        manager.mongoDatastore == null
        manager.mongoClient == null
        manager.grailsApplication == null
        manager.mappingContext == null

        then:
        noExceptionThrown()

        cleanup:
        manager.cleanupMultiDataSource()
        manager.cleanup()
        manager.cleanupSpec()
    }
}



