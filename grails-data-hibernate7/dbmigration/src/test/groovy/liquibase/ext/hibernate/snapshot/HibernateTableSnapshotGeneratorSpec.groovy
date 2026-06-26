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
package liquibase.ext.hibernate.snapshot

import liquibase.structure.core.Schema
import liquibase.structure.core.Table

class HibernateTableSnapshotGeneratorSpec extends HibernateSnapshotIntegrationSpec {

    HibernateTableSnapshotGenerator generator = new HibernateTableSnapshotGenerator()

    @Override
    List<Class> getEntityClasses() {
        return [AuctionItem, Bid, AuctionUser]
    }

    def "snapshotObject returns table with name"() {
        given:
        Table example = new Table(name: "auction_item")

        when:
        def result = generator.snapshotObject(example, snapshot)

        then:
        result instanceof Table
        result.name == "auction_item"
    }

    def "addTo adds tables to schema"() {
        given:
        Schema schema = new Schema()
        snapshot.getSnapshotControl().shouldInclude(Table) >> true

        when:
        generator.addTo(schema, snapshot)

        then:
        schema.getDatabaseObjects(Table).any { it.name == "auction_item" }
    }
}
