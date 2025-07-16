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

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.convert.value.ConvertibleValuesMap;
import org.grails.forge.application.OperatingSystem;
import org.grails.forge.util.VersionInfo;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class Options implements ConvertibleValues<Object> {

    private final OperatingSystem operatingSystem;

    private final TestFramework testFramework;
    private final BuildTool buildTool;
    private final GormImpl gormImpl;
    private final ServletImpl servletImpl;
    private final JdkVersion javaVersion;
    private final ConvertibleValuesMap<Object> additionalOptions;

    public Options(TestFramework testFramework,
                   GormImpl gormImpl,
                   ServletImpl servletImpl,
                   JdkVersion javaVersion,
                   OperatingSystem operatingSystem,
                   Map<String, Object> additionalOptions) {

        this.testFramework = testFramework;
        this.buildTool = BuildTool.DEFAULT_OPTION;
        this.gormImpl = gormImpl;
        this.servletImpl = servletImpl;
        this.javaVersion = javaVersion;
        this.operatingSystem = operatingSystem;
        this.additionalOptions = new ConvertibleValuesMap<>(additionalOptions);
    }

    public Options(TestFramework testFramework,
                   GormImpl gormImpl,
                   ServletImpl servletImpl,
                   JdkVersion javaVersion,
                   OperatingSystem operatingSystem) {

        this(testFramework, gormImpl, servletImpl, javaVersion, operatingSystem, Collections.emptyMap());
    }

    public Options(TestFramework testFramework,
                   JdkVersion javaVersion,
                   OperatingSystem operatingSystem) {

        this(testFramework, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, javaVersion, operatingSystem, Collections.emptyMap());
    }

    public Options(TestFramework testFramework,
                   OperatingSystem operatingSystem) {

        this(testFramework, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, VersionInfo.getJavaVersion(), operatingSystem, Collections.emptyMap());
    }

    public Options(TestFramework testFramework,
                   GormImpl gormImpl,
                   ServletImpl servletImpl,
                   JdkVersion javaVersion) {

        this(testFramework, gormImpl, servletImpl, javaVersion, OperatingSystem.DEFAULT, Collections.emptyMap());
    }

    public Options(TestFramework testFramework,
                   JdkVersion javaVersion) {

        this(testFramework, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, javaVersion, OperatingSystem.DEFAULT, Collections.emptyMap());
    }

    public Options(TestFramework testFramework) {
        this(testFramework, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, OperatingSystem.DEFAULT, Collections.emptyMap());
    }

    public Options() {
        this(TestFramework.DEFAULT_OPTION, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, OperatingSystem.DEFAULT, Collections.emptyMap());
    }

    public OperatingSystem getOperatingSystem() {
        return operatingSystem;
    }

    public TestFramework getTestFramework() {
        return testFramework;
    }

    public BuildTool getBuildTool() {
        return buildTool;
    }

    public GormImpl getGormImpl() {
        return gormImpl;
    }

    public ServletImpl getServletImpl() {
        return servletImpl;
    }

    @Override
    public Set<String> names() {
        return additionalOptions.names();
    }

    @Override
    public Collection<Object> values() {
        return additionalOptions.values();
    }

    @Override
    public <T> Optional<T> get(CharSequence name, ArgumentConversionContext<T> conversionContext) {
        return additionalOptions.get(name, conversionContext);
    }

    public JdkVersion getJavaVersion() {
        return javaVersion;
    }

    public Options withOperatingSystem(OperatingSystem operatingSystem) {
        return new Options(testFramework, gormImpl, servletImpl, javaVersion, operatingSystem, additionalOptions.asMap());
    }

    public Options withTestFramework(TestFramework testFramework) {
        return new Options(testFramework, gormImpl, servletImpl, javaVersion, operatingSystem, additionalOptions.asMap());
    }

    public Options withGormImpl(GormImpl gormImpl) {
        return new Options(testFramework, gormImpl, servletImpl, javaVersion, operatingSystem, additionalOptions.asMap());
    }

    public Options withServletImpl(ServletImpl servletImpl) {
        return new Options(testFramework, gormImpl, servletImpl, javaVersion, operatingSystem, additionalOptions.asMap());
    }

    public Options withJavaVersion(JdkVersion javaVersion) {
        return new Options(testFramework, gormImpl, servletImpl, javaVersion, operatingSystem, additionalOptions.asMap());
    }
}
