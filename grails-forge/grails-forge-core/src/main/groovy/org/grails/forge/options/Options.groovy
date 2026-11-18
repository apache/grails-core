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
package org.grails.forge.options

import groovy.transform.CompileStatic
import org.grails.forge.application.OperatingSystem
import org.grails.forge.util.VersionInfo

@CompileStatic
class Options {

    private final OperatingSystem operatingSystem

    private final DevelopmentReloading reloading
    private final BuildTool buildTool
    private final GormImpl gormImpl
    private final ServletImpl servletImpl
    private final JdkVersion javaVersion

    Options(DevelopmentReloading reloading,
                   GormImpl gormImpl,
                   ServletImpl servletImpl,
                   JdkVersion javaVersion,
                   OperatingSystem operatingSystem) {

        this.reloading = reloading
        this.buildTool = BuildTool.DEFAULT_OPTION
        this.gormImpl = gormImpl
        this.servletImpl = servletImpl
        this.javaVersion = javaVersion
        this.operatingSystem = operatingSystem
    }

    Options(DevelopmentReloading reloading,
                   JdkVersion javaVersion,
                   OperatingSystem operatingSystem) {

        this(reloading, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, javaVersion, operatingSystem)
    }

    Options(DevelopmentReloading reloading,
                   OperatingSystem operatingSystem) {

        this(reloading, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, VersionInfo.getJavaVersion(), operatingSystem)
    }

    Options(DevelopmentReloading reloading,
                   GormImpl gormImpl,
                   ServletImpl servletImpl,
                   JdkVersion javaVersion) {

        this(reloading, gormImpl, servletImpl, javaVersion, OperatingSystem.DEFAULT)
    }

    Options(DevelopmentReloading reloading,
                   JdkVersion javaVersion) {

        this(reloading, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, javaVersion, OperatingSystem.DEFAULT)
    }

    Options(DevelopmentReloading reloading) {
        this(reloading, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, OperatingSystem.DEFAULT)
    }

    Options() {
        this(DevelopmentReloading.DEFAULT_OPTION, GormImpl.DEFAULT_OPTION, ServletImpl.DEFAULT_OPTION, JdkVersion.DEFAULT_OPTION, OperatingSystem.DEFAULT)
    }

    OperatingSystem getOperatingSystem() {
        return operatingSystem
    }

    DevelopmentReloading getDevelopmentReloading() {
        return reloading
    }

    BuildTool getBuildTool() {
        return buildTool
    }

    GormImpl getGormImpl() {
        return gormImpl
    }

    ServletImpl getServletImpl() {
        return servletImpl
    }

    JdkVersion getJavaVersion() {
        return javaVersion
    }

    Options withOperatingSystem(OperatingSystem operatingSystem) {
        return new Options(reloading, gormImpl, servletImpl, javaVersion, operatingSystem)
    }

    Options withDevelopmentReloading(DevelopmentReloading reloading) {
        return new Options(reloading, gormImpl, servletImpl, javaVersion, operatingSystem)
    }

    Options withGormImpl(GormImpl gormImpl) {
        return new Options(reloading, gormImpl, servletImpl, javaVersion, operatingSystem)
    }

    Options withServletImpl(ServletImpl servletImpl) {
        return new Options(reloading, gormImpl, servletImpl, javaVersion, operatingSystem)
    }

    Options withJavaVersion(JdkVersion javaVersion) {
        return new Options(reloading, gormImpl, servletImpl, javaVersion, operatingSystem)
    }
}
