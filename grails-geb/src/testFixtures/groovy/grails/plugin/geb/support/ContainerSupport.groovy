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
package grails.plugin.geb.support

import geb.download.DownloadSupport
import grails.plugin.geb.ContainerGebSpec
import groovy.transform.CompileStatic
import groovy.transform.SelfType
import org.testcontainers.containers.BrowserWebDriverContainer
import org.testcontainers.images.builder.Transferable
import spock.lang.Shared

/**
 * Features for supporting Geb tests running in a container.
 *
 * @author Mattias Reichel
 * @since 4.2
 */
@CompileStatic
@SelfType(ContainerGebSpec)
trait ContainerSupport implements DownloadSupport {

    /**
     * Get access to container running the web-driver, for convenience to execInContainer, copyFileToContainer etc.
     *
     * @see org.testcontainers.containers.ContainerState#execInContainer(java.lang.String ...)
     * @see org.testcontainers.containers.ContainerState#copyFileToContainer(org.testcontainers.utility.MountableFile, java.lang.String)
     * @see org.testcontainers.containers.ContainerState#copyFileFromContainer(java.lang.String, java.lang.String)
     * @see org.testcontainers.containers.ContainerState
     */
    @Shared
    static BrowserWebDriverContainer container

    static void setContainer(BrowserWebDriverContainer container) {
        this.container = container
    }

    @Shared
    static DownloadSupport downloadSupport

    /**
     * Sets the {@link DownloadSupport} instance to use for file downloads.
     * This allows for setting a custom implementation of {@code DownloadSupport}
     * when downloading from a container.
     *
     * @param downloadSupport the {@code DownloadSupport} instance to use
     * @since 4.1
     */
    static void setDownloadSupport(DownloadSupport downloadSupport) {
        this.downloadSupport = downloadSupport
    }

    /**
     * Copies a file from the host to the container for assignment to a Geb FileInput module.
     * This method is useful when you need to upload a file to a form in a Geb test and will work cross-platform.
     *
     * @param hostPath relative path to the file on the host
     * @param containerPath absolute path to where to put the file in the container
     * @return the file object to assign to the FileInput module
     * @since 4.2
     */
    File createFileInputSource(String hostPath, String containerPath) {
        container.copyFileToContainer(Transferable.of(new File(hostPath).bytes), containerPath)
        return new ContainerGebFileInputSource(containerPath)
    }
}