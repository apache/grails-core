/*
 * Copyright 2004-2005 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.core;

import grails.core.GrailsServiceClass;

/**
 * @author Steven Devijver
 */
public class DefaultGrailsServiceClass extends AbstractInjectableGrailsClass implements GrailsServiceClass {

    public static final String SERVICE = "Service";
    private static final String TRANSACTIONAL = "transactional";

    private boolean transactional = true;
    private String datasourceName;

    public DefaultGrailsServiceClass(Class<?> clazz) {
        super(clazz, SERVICE);

        Object tmpTransactional = getStaticPropertyValue(TRANSACTIONAL, Boolean.class);
        transactional = Boolean.TRUE.equals(tmpTransactional);
    }

    public boolean isTransactional() {
        return transactional;
    }

    /**
     * If service is transactional then get data source will always apply
     *
     * @return name of data source
     */
    public String getDatasource() {
        if (datasourceName == null) {
            CharSequence name = getStaticPropertyValue(DATA_SOURCE, CharSequence.class);
            datasourceName = name == null ? null : name.toString();
            if (datasourceName == null) {
                datasourceName = DEFAULT_DATA_SOURCE;
            }
        }

        return datasourceName;
    }

    public boolean usesDatasource(final String name) {
        return getDatasource().equals(name);
    }
}
