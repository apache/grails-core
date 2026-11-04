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

package gorm

import org.hibernate.proxy.HibernateProxy

/**
 * Controller used by the functional tests for issues 15681 and 15795. It binds the incoming request
 * parameters to a new domain instance, or to a proxy of a persisted one, and renders the resulting
 * {@code id}, {@code version} and {@code description} so the tests can assert over HTTP which properties
 * were bound.
 */
class DirtyCheckBindingController {

    def bind() {
        def record = new DirtyCheckedRecord()
        bindData(record, params)
        render "id=${record.id}|version=${record.version}|description=${record.description}"
    }

    def bindInheritedBindableId() {
        def record = new BindableIdRecord()
        bindData(record, params)
        render "id=${record.id}|version=${record.version}|description=${record.description}"
    }

    def bindProxy(Long recordId) {
        // A new request's session holds nothing yet, so the persisted record is loaded as a proxy.
        def record = DirtyCheckedRecord.load(recordId)
        boolean proxy = record instanceof HibernateProxy
        bindData(record, params)
        String bound = "proxy=${proxy}|id=${record.id}|version=${record.version}|description=${record.description}"
        record.discard()
        render bound
    }
}
