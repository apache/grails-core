/*******************************************************************************
 * Copyright 2014 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package org.grails.datastore.gorm.timestamp;

import java.util.Collections;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * TimestampProvider implementation that aggregates multiple TimestampProviders
 *
 */
public class AggregateTimestampProvider implements TimestampProvider {
    private List<TimestampProvider> timestampProviders=Collections.emptyList();
    
    @Override
    public boolean supportsCreating(Class<?> dateTimeClass) {
        for(TimestampProvider provider : timestampProviders) {
            if(provider.supportsCreating(dateTimeClass)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public <T> T createTimestamp(Class<T> dateTimeClass) {
        if(timestampProviders.size() > 1) {
            for(TimestampProvider provider : timestampProviders) {
                if(provider.supportsCreating(dateTimeClass)) {
                    return createTimestamp(provider, dateTimeClass);
                }
            }
        } else {
            return createTimestamp(timestampProviders.iterator().next(), dateTimeClass);
        }
        throw new IllegalArgumentException("dateTimeClass given as parameter isn't supported by any TimestampProvider. You should call supportsCreating first.");
    }

    protected <T> T createTimestamp(TimestampProvider provider, Class<T> dateTimeClass) {
        return provider.createTimestamp(dateTimeClass);
    }

    public List<TimestampProvider> getTimestampProviders() {
        return timestampProviders;
    }

    @Autowired
    public void setTimestampProviders(List<TimestampProvider> timestampProviders) {
        this.timestampProviders = timestampProviders;
    }
}
