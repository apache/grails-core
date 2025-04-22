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

import java.util.Properties;

/**
 * Represents a data source in Grails.
 *
 * @author Steven Devijver
 */
public interface GrailsDataSource extends InjectableGrailsClass {

    /**
     * True is connection pooling is enabled.
     *
     * @return connection pooling enabled
     */
    boolean isPooled();

    /**
     * The driver class name for the data source.
     *
     * @return driver class name
     */
    String getDriverClassName();

    /**
     * The URL for the data source.
     *
     * @return URL
     */
    String getUrl();

    /**
     * The username for the data source.
     *
     * @return username
     */
    String getUsername();

    /**
     * The password for the data source.
     *
     * @return password
     */
    String getPassword();

    /**
     * Other properties for this data source.
     *
     * @return other properties
     */
    Properties getOtherProperties();

    /**
     * Whether to generate the database with HBM 2 DDL, values can be "create", "create-drop" or "update".
     * @return The dbCreate method to use
     */
    String getDbCreate();

    /**
     * @return The configuration class to use when setting up the database.
     */
    @SuppressWarnings("rawtypes")
    Class getConfigurationClass();

    /**
     * The dialect implementation to use.
     * @return The dialect class or null if none configured
     */
    @SuppressWarnings("rawtypes")
    Class getDialect();

    /**
     * Whether SQL logging is enabled
     *
     * @return true if SQL logging is enabled
     */
    boolean isLoggingSql();
}
