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

package org.grails.orm.hibernate.cfg.domainbinding.secondpass


/**
 * Domain classes of the former {@code MapSecondPassBinderSpec}, a unit spec of the classic domain binder of Grails 8: they stay as fixtures of the
 * differential specs, which compare native binding with the recorded classic schema of every scanned entity.
 */
@grails.gorm.annotation.Entity
class MapSPBAuthor {
    Long id
    Map<String, MapSPBBook> books
    static hasMany = [books: MapSPBBook]
    static mapping = {
        books index: {
            column 'books_idx'
        }
    }
}

@grails.gorm.annotation.Entity
class MapSPBBook {
    Long id
    String title
}

@grails.gorm.annotation.Entity
class MapSPBOwner {
    Long id
    Map<String, String> attributes
    static hasMany = [attributes: String]
}
