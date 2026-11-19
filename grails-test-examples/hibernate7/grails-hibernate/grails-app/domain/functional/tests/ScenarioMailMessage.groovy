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
 * A message whose recipient lists are element collections stored in explicit join tables.
 */
class ScenarioMailMessage {

    String subject

    static hasMany = [tos: String, ccs: String]

    static mapping = {
        tos joinTable: [name: 'scenario_mail_to', key: 'message_id', column: 'addr', type: 'text']
        ccs joinTable: [name: 'scenario_mail_cc', key: 'message_id', column: 'addr', type: 'text']
    }
}
