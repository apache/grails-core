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
package functionaltests.interceptors

import grails.converters.JSON

/**
 * Controller for testing advanced interceptor matching patterns.
 */
class AdvancedMatchingController {

    static namespace = 'api'

    /**
     * Tracks which interceptors have been applied to this request.
     */
    static List<String> appliedInterceptors = []

    static void recordInterceptor(String name) {
        appliedInterceptors << name
    }

    static void resetInterceptors() {
        appliedInterceptors = []
    }

    def index() {
        render([
            action: 'index',
            namespace: 'api',
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def list() {
        render([
            action: 'list',
            namespace: 'api',
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def show() {
        render([
            action: 'show',
            namespace: 'api',
            id: params.id,
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def create() {
        render([
            action: 'create',
            namespace: 'api',
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def save() {
        render([
            action: 'save',
            namespace: 'api',
            method: request.method,
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def update() {
        render([
            action: 'update',
            namespace: 'api',
            method: request.method,
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def delete() {
        render([
            action: 'delete',
            namespace: 'api',
            method: request.method,
            interceptors: new ArrayList(appliedInterceptors)
        ] as JSON)
    }

    def reset() {
        resetInterceptors()
        render([reset: true] as JSON)
    }
}
