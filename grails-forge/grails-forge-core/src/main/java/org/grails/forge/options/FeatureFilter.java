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
package org.grails.forge.options;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

@Introspected
public class FeatureFilter {
    @JsonProperty("test")
    private TestFramework test;
    @JsonProperty("gorm")
    private GormImpl gorm;
    @JsonProperty("servlet")
    private ServletImpl servlet;
    @JsonProperty("javaVersion")
    private JdkVersion javaVersion;

    public TestFramework getTest() {
        return test;
    }

    public void setTest(TestFramework test) {
        this.test = test;
    }

    public GormImpl getGorm() {
        return gorm;
    }

    public void setGorm(GormImpl gorm) {
        this.gorm = gorm;
    }

    public ServletImpl getServlet() {
        return servlet;
    }

    public void setServlet(ServletImpl servlet) {
        this.servlet = servlet;
    }

    public JdkVersion getJavaVersion() {
        return javaVersion;
    }

    public void setJavaVersion(JdkVersion javaVersion) {
        this.javaVersion = javaVersion;
    }
}
