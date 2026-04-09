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

package example

import grails.gorm.services.Service
import grails.gorm.transactions.Transactional
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.gorm.GormStaticApi

/**
 * GORM Data Service for Metric, routed to the 'secondary' datasource
 * via @Transactional(connection). Combines DISCRIMINATOR multi-tenancy
 * with a non-default datasource - the scenario that triggers the
 * allQualifiers() bug.
 *
 * All auto-implemented methods (save, get, delete, findByName, count)
 * should route to the secondary datasource with proper tenant isolation.
 */
@Service(Metric)
@Transactional(connection = 'secondary')
abstract class MetricService {

    abstract Metric get(Serializable id)

    abstract Metric save(Metric metric)

    abstract Metric delete(Serializable id)

    abstract Number count()

    abstract Metric findByName(String name)

    abstract List<Metric> findAllByName(String name)

    /**
     * Statically compiled access to the secondary datasource via GormEnhancer.
     */
    private GormStaticApi<Metric> getSecondaryApi() {
        GormEnhancer.findStaticApi(Metric, 'secondary')
    }

    /**
     * Delete all metrics for the current tenant from the secondary datasource.
     */
    void deleteAll() {
        secondaryApi.executeUpdate('delete from Metric', [:])
    }
}
