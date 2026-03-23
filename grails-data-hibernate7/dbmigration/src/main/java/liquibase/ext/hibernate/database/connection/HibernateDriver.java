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
package liquibase.ext.hibernate.database.connection;

import java.sql.*;
import java.util.Properties;
import java.util.logging.Logger;

import liquibase.database.LiquibaseExtDriver;
import liquibase.resource.ResourceAccessor;

/**
 * Implements the standard java.sql.Driver interface to allow the Hibernate integration to better fit into
 * what Liquibase expects.
 */
public class HibernateDriver implements Driver, LiquibaseExtDriver {

    private ResourceAccessor resourceAccessor;

    @Override
    public Connection connect(String url, Properties info)  {
        return new HibernateConnection(url, resourceAccessor);
    }

    @Override
    public boolean acceptsURL(String url)  {
        return url.startsWith("hibernate:");
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info)  {
        return new DriverPropertyInfo[0];
    }

    @Override
    public int getMajorVersion() {
        return 0;
    }

    @Override
    public int getMinorVersion() {
        return 0;
    }

    @Override
    public boolean jdbcCompliant() {
        return false;
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException();
    }

    @Override
    public void setResourceAccessor(ResourceAccessor accessor) {
        this.resourceAccessor = accessor;
    }
}
