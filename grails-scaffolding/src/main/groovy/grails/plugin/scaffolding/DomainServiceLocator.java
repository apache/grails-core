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

package grails.plugin.scaffolding;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.context.ApplicationContext;

import grails.util.Environment;
import grails.util.Holders;
import org.grails.datastore.gorm.GormEntity;

/**
 * Resolves the appropriate service bean for a given domain class by:
 *   - Scanning only beans of type GormService
 *   - Matching via: domainClass.isAssignableFrom(service.getResource())
 *
 * Keeps a single shared cache for the whole app.
 */
public final class DomainServiceLocator {

    private static final ConcurrentMap<Class<?>, GormService<?>> CACHE = new ConcurrentHashMap<>();

    private DomainServiceLocator() {}

    /** Resolve (and cache) a service bean for the given domain class. */
    public static <T extends GormEntity<T>> GormService<T> resolve(Class<T> domainClass) {
        if (!Environment.isDevelopmentMode()) {
            @SuppressWarnings("unchecked")
            GormService<T> cached = (GormService<T>) CACHE.get(domainClass);
            if (cached != null) return cached;
        }

        GormService<T> found = findService(domainClass);
        if (!Environment.isDevelopmentMode()) {
            CACHE.put(domainClass, found);
        }
        return found;
    }

    /** Clear cache (useful in tests/dev reloads). */
    public static void clear() {
        CACHE.clear();
    }

    private static <T extends GormEntity<T>> GormService<T> findService(Class<T> domainClass) {
        ApplicationContext ctx = Holders.getGrailsApplication().getMainContext();

        String[] names = ctx.getBeanNamesForType(GormService.class);
        GormService<T> match = null;
        List<String> matchingBeanNames = new ArrayList<>();

        for (String name : names) {
            GormService<?> gs = (GormService<?>) ctx.getBean(name);
            Class<?> resourceClass = gs.getResource();
            if (resourceClass != null && domainClass.isAssignableFrom(resourceClass)) {
                matchingBeanNames.add(name);
                if (match != null) {
                    throw new IllegalStateException(
                        "Multiple GormService beans match domain " + domainClass.getName() +
                        ": " + matchingBeanNames
                    );
                }
                @SuppressWarnings("unchecked")
                GormService<T> svc = (GormService<T>) gs;
                match = svc;
            }
        }

        if (match == null) {
            throw new IllegalStateException(
                "No GormService bean found for domain " + domainClass.getName() +
                ". Scanned " + names.length + " GormService beans."
            );
        }

        return match;
    }
}

