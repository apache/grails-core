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

import grails.gorm.annotation.Entity

@Entity
class AuctionItem {
    String description
    String shortDescription
    Date ends
    Integer condition

    static hasMany = [bids: Bid]

    static constraints = {
        description nullable: true, maxSize: 1000
        shortDescription nullable: true, maxSize: 200
        ends nullable: true
        condition nullable: true
    }
}

@Entity
class Bid {
    Float amount
    Date datetime

    static belongsTo = [item: AuctionItem, bidder: AuctionUser]

    static constraints = {
        datetime nullable: false
    }
}

@Entity
class AuctionUser {
    String userName
    String email

    static hasMany = [bids: Bid]

    static constraints = {
        userName nullable: true
        email nullable: true
    }
}
