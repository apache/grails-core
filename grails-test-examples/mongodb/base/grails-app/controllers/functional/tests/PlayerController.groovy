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

package functional.tests

/**
 * Actions that run without a transaction, so the only session they can read through is the
 * one the request keeps open.
 */
class PlayerController {

    static allowedMethods = [save: 'POST', rename: 'POST']

    def count() {
        render text: Player.list().size().toString()
    }

    def save(String name, String teamId) {
        render text: new Player(name: name, team: Team.get(teamId)).save().id.toString()
    }

    def rename(String id, String name) {
        Player.get(id).name = name
        render text: 'renamed without saving'
    }
}
