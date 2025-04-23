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

package org.grails.datastore.mapping.core.connections;

import org.grails.datastore.mapping.engine.types.CustomTypeMarshaller;
import org.grails.datastore.mapping.multitenancy.TenantResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.PropertyResolver;

import java.util.List;

/**
 * Abstract implementation of the {@link ConnectionSourceFactory} interface
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractConnectionSourceFactory<T, S extends ConnectionSourceSettings> implements ConnectionSourceFactory<T, S> {

    private TenantResolver tenantResolver;
    private List<CustomTypeMarshaller> customTypes;

    /**
     * The tenant resolver to use
     *
     * @param tenantResolver The tenant resolver
     */
    @Autowired(required = false)
    public void setTenantResolver(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    /**
     * The custom user types to register
     *
     * @param customTypes The custom user types
     */
    @Autowired(required = false)
    public void setCustomTypes(List<CustomTypeMarshaller> customTypes) {
        this.customTypes = customTypes;
    }

    @Override
    public ConnectionSource<T, S> create(String name, PropertyResolver configuration) {
        ConnectionSourceSettingsBuilder builder = new ConnectionSourceSettingsBuilder(configuration);
        ConnectionSourceSettings fallbackSettings = builder.build();
        if(tenantResolver != null) {
            fallbackSettings.getMultiTenancy().setTenantResolver(tenantResolver);
        }
        if(customTypes != null) {
            fallbackSettings.getCustom().getTypes().addAll( this.customTypes );
        }
        return create(name, configuration, fallbackSettings);
    }

    @Override
    public ConnectionSource<T, S> create(String name, PropertyResolver configuration, ConnectionSource<T, S> fallbackConnectionSource) {
        return create(name, configuration, fallbackConnectionSource.getSettings());
    }

    @Override
    public final <F extends ConnectionSourceSettings> ConnectionSource<T, S> create(String name, PropertyResolver configuration, F fallbackSettings) {
        boolean isDefaultDataSource = ConnectionSource.DEFAULT.equals(name);
        S settings = buildSettings(name, configuration, fallbackSettings, isDefaultDataSource);
        return create(name, settings);
    }

    @Override
    public ConnectionSource<T, S> createRuntime(String name, PropertyResolver configuration, S fallbackSettings) {
        S settings = buildRuntimeSettings(name, configuration, fallbackSettings);
        return create(name, settings);
    }
    
    public <F extends ConnectionSourceSettings> S buildRuntimeSettings(String name, PropertyResolver configuration, F fallbackSettings) {
        return buildSettings(name, configuration, fallbackSettings, false);
    }

    protected abstract <F extends ConnectionSourceSettings> S buildSettings(String name, PropertyResolver configuration, F fallbackSettings, boolean isDefaultDataSource);
}
