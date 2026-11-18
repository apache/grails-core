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

package namespaces

import grails.gorm.transactions.ReadOnly

class LinkFixtureService {

    @ReadOnly
    Map model(Map params) {
        [
                assessment : Assessment.get(params.assessmentId as Long),
                film       : Film.get(params.filmId as Long),
                gadget     : Gadget.get(params.gadgetId as Long),
                catalogItem: namespaces.catalog.Item.get(params.catalogItemId as Long),
                archiveItem: namespaces.archive.Item.get(params.archiveItemId as Long),
                screening  : Screening.get(params.screeningId as Long)
        ]
    }
}
