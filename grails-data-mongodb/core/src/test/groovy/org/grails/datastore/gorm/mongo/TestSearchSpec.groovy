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
package org.grails.datastore.gorm.mongo

import grails.mongodb.MongoEntity
import grails.persistence.Entity
import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec
import org.bson.types.ObjectId

/**
 * Created by graemerocher on 14/04/14.
 */
class TestSearchSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {

    void setupSpec() {
        manager.domainClasses.addAll([Product])
    }

    void "Test simple text search"() {
        given: "Some sample data"
        new Product(title: "Italian Coffee").save()
        new Product(title: "Arabian Coffee").save()
        new Product(title: "Coffee Maker").save()
        new Product(title: "Coffee Grinder").save()
        new Product(title: "Coffee Cake").save()
        new Product(title: "Apple Cake").save()
        new Product(title: "Chocolate Cake").save()
        new Product(title: "Cheese Bake").save()
        new Product(title: "Bake a Cake").save()
        new Product(title: "Potato Bake").save(flush: true)

        expect: "The results are correct"
        Product.search("coffee").size() == 5
        Product.search("bake coffee cake").size() == 10
        Product.search("bake coffee -cake").size() == 6
        Product.search('"Coffee Cake"').size() == 1
        Product.searchTop("cake").size() == 4
        Product.searchTop("cake", 3).size() == 3
        Product.countHits('coffee') == 5
    }
}

@Entity
class Product implements MongoEntity<Product> {

    ObjectId id
    String title

    static mapping = {
        index title: "text"
    }

    @Override
    String toString() {
        title
    }
}
