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
package startupprogress

import java.time.Duration

import grails.boot.StartupTask
import grails.config.Config
import grails.core.GrailsApplication

/**
 * Takes {@code startup.demo.bootstrapDelay} to seed the catalog, which the progress page covers because it
 * keeps a browser waiting until BootStrap has finished, and fails when {@code startup.demo.fail} is
 * {@code bootstrap}. It reports the seeding as a {@link StartupTask}, one item to each product, which a
 * signed-in progress page shows as it runs.
 */
class BootStrap {

    GrailsApplication grailsApplication
    CatalogService catalogService

    def init = {
        Config config = grailsApplication.config
        List<String> products = ['Tea', 'Coffee', 'Cocoa']
        long delayEach = config.getProperty('startup.demo.bootstrapDelay', Duration, Duration.ZERO).toMillis().intdiv(products.size())
        try (StartupTask task = StartupTask.start(grailsApplication.mainContext, 'Seeding the catalog', products.size())) {
            for (String product in products) {
                task.startItem(product)
                Thread.sleep(delayEach)
                if (config.getProperty('startup.demo.fail', String) == 'bootstrap') {
                    throw new IllegalStateException('Could not seed the catalog: the inventory service at <inventory.internal> refused the connection')
                }
                catalogService.seed([product])
                task.endItem()
            }
        }
    }

    def destroy = {
    }
}
