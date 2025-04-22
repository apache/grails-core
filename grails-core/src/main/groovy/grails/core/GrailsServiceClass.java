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
package grails.core;

/**
 * @author Steven Devijver
 */
public interface GrailsServiceClass extends InjectableGrailsClass {

    String DATA_SOURCE = "datasource";
    String DEFAULT_DATA_SOURCE = "DEFAULT";
    String ALL_DATA_SOURCES = "ALL";

    /**
     * Service should be configured with transaction demarcation.
     *
     * @return configure with transaction demarcation
     */
    boolean isTransactional();

    /**
     * Get the datasource name that this service class works with.
     * @return the name
     */
    String getDatasource();

    /**
     * Check if the service class can use the named DataSource.
     * @param name the name
     */
    boolean usesDatasource(String name);
}
