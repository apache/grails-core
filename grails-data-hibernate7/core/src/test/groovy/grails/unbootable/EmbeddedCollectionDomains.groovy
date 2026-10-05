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
package grails.unbootable

import grails.gorm.annotation.Entity

/**
 * Domain classes that embed a type with a collection, kept outside the packages the differential spec scans: the binder names the
 * table and the key column of the collection after the embedded type, so two owners of the type share one collection table.
 */
@Entity
class EmbeddedCollectionOwnerA {

    EmbeddedCollectionHolder inner

    static embedded = ['inner']
}

@Entity
class EmbeddedCollectionOwnerB {

    EmbeddedCollectionHolder inner

    static embedded = ['inner']
}

class EmbeddedCollectionHolder {

    Set<String> words

    static hasMany = [words: String]
}
