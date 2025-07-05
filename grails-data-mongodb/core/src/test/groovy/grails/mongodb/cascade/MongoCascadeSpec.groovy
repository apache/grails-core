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
package grails.mongodb.cascade

import org.apache.grails.data.mongo.core.GrailsDataMongoTckManager
import org.apache.grails.data.testing.tck.base.GrailsDataTckSpec

class MongoCascadeSpec extends GrailsDataTckSpec<GrailsDataMongoTckManager> {
    void setupSpec() {
        manager.addAllDomainClasses([Product, ProductLine])
    }

    void "test association is not cascaded on update or insert"() {
        given:
        ProductLine x = new ProductLine(name: "x")
        x.save()
        manager.session.flush()
        manager.session.clear()

        Product product = new Product(name: "my product", productLine: ProductLine.load(x.id))
        product.save()
        manager.session.flush()
        manager.session.clear()

        when:
        product = Product.get(product.id)

        then:
        product.productLine.name == "x"

        when: //no cascading on update
        product.productLine.name = "xy"
        product.save()
        manager.session.flush()
        manager.session.clear()
        x = ProductLine.get(x.id)

        then:
        x.name == "x"

        when:
        x.name = "xy"
        product = new Product(name: "other product", productLine: x)
        product.save()
        manager.session.flush()
        manager.session.clear()

        then:
        ProductLine.get(x.id).name == "x"
    }
}
