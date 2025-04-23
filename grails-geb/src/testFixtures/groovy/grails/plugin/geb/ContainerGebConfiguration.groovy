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
package grails.plugin.geb

import org.testcontainers.containers.GenericContainer

import java.lang.annotation.ElementType
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Can be used to configure the protocol and hostname that the container's browser will use
 *
 * @author James Daugherty
 * @since 4.1
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@interface ContainerGebConfiguration {

    static final String DEFAULT_HOSTNAME_FROM_CONTAINER = GenericContainer.INTERNAL_HOST_HOSTNAME
    static final String DEFAULT_PROTOCOL = 'http'
    static final Class<? extends ContainerFileDetector> DEFAULT_FILE_DETECTOR = DefaultContainerFileDetector

    /**
     * The protocol that the container's browser will use to access the server under test.
     * <p>Defaults to {@code http}.
     */
    String protocol() default DEFAULT_PROTOCOL

    /**
     * The hostname that the container's browser will use to access the server under test.
     * <p>Defaults to {@code host.testcontainers.internal}.
     * <p>This is useful when the server under test needs to be accessed with a certain hostname.
     */
    String hostName() default DEFAULT_HOSTNAME_FROM_CONTAINER

    /**
     * Whether reporting should be enabled for this test. Add a `GebConfig.groovy` to customize the reporter configuration.
     */
    boolean reporting() default false

    /**
     * The {@link org.openqa.selenium.remote.FileDetector} implementation to use for this class.
     * <p> {@link NullContainerFileDetector} results in the
     *     {@link grails.plugin.geb.serviceloader.ServiceRegistry last set} instance being used.
     *
     * @since 4.2
     * @see grails.plugin.geb.DefaultContainerFileDetector DefaultContainerFileDetector
     * @see grails.plugin.geb.UselessContainerFileDetector UselessContainerFileDetector
     */
    Class<? extends ContainerFileDetector> fileDetector() default DefaultContainerFileDetector
}

/**
 * Inheritable version of {@link ContainerGebConfiguration}
 *
 * @since 4.2
 */
interface IContainerGebConfiguration {

    default String protocol() {
        ContainerGebConfiguration.DEFAULT_PROTOCOL
    }

    default String hostName() {
        ContainerGebConfiguration.DEFAULT_HOSTNAME_FROM_CONTAINER
    }

    default boolean reporting() {
        false
    }

    default Class<? extends ContainerFileDetector> fileDetector() {
        ContainerGebConfiguration.DEFAULT_FILE_DETECTOR
    }
}
