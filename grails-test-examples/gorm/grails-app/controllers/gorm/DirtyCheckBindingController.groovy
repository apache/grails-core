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

/**
 * Controller used by the functional test for issue 15681. It binds the incoming request parameters to a new
 * {@link DirtyCheckedRecord} and renders the resulting {@code id}, {@code version} and {@code description} so the
 * test can assert over HTTP that {@code id}/{@code version} were not bound by default.
 */
class DirtyCheckBindingController {

    def bind() {
        def record = new DirtyCheckedRecord()
        bindData(record, params)
        render "id=${record.id}|version=${record.version}|description=${record.description}"
    }
}
