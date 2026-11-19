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

import grails.gorm.services.Service
import grails.gorm.services.Where

/**
 * Data service queries whose where closure compares a property with an aggregate of the same property.
 */
@Service(ScenarioItem)
interface ScenarioItemService {

    @Where({ qty > avg(qty) })
    List<ScenarioItem> aboveAverage()

    @Where({ qty >= avg(qty) })
    List<ScenarioItem> atLeastAverage()

    @Where({ qty < avg(qty) })
    List<ScenarioItem> belowAverage()

    @Where({ qty <= avg(qty) })
    List<ScenarioItem> atMostAverage()

    @Where({ qty == max(qty) })
    List<ScenarioItem> highest()

    @Where({ qty == min(qty) })
    List<ScenarioItem> lowest()

    @Where({ qty > avg(qty) && status == wanted })
    List<ScenarioItem> aboveAverageWithStatus(ScenarioStatus wanted)
}
