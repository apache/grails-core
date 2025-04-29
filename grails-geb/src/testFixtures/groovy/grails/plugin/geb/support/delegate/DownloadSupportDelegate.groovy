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
package grails.plugin.geb.support.delegate

import geb.download.DownloadSupport
import grails.plugin.geb.ContainerGebSpec
import groovy.transform.CompileStatic
import groovy.transform.SelfType

/**
 * Handles delegation to the DownloadSupport instance so that the Geb API can be used directly in the test.
 * <p>
 * As method parameter names are not available in the Geb artifacts we are delegating manually to
 * get the best possible IDE support and user experience.
 *
 * @author Mattias Reichel
 * @since 4.2
 */
@CompileStatic
@SelfType(ContainerGebSpec)
trait DownloadSupportDelegate implements DownloadSupport {

    @Override
    HttpURLConnection download() {
        downloadSupport.download()
    }

    @Override
    HttpURLConnection download(Map options) {
        downloadSupport.download(options)
    }

    @Override
    HttpURLConnection download(String uri) {
        downloadSupport.download(uri)
    }

    @Override
    InputStream downloadStream() {
        downloadSupport.downloadStream()
    }

    @Override
    InputStream downloadStream(Map options) {
        downloadSupport.downloadStream(options)
    }

    @Override
    InputStream downloadStream(Map options, Closure connectionConfig) {
        downloadSupport.downloadStream(options, connectionConfig)
    }

    @Override
    InputStream downloadStream(String uri) {
        downloadSupport.downloadStream(uri)
    }

    @Override
    InputStream downloadStream(String uri, Closure connectionConfig) {
        downloadSupport.downloadStream(uri, connectionConfig)
    }

    @Override
    InputStream downloadStream(Closure connectionConfig) {
        downloadSupport.downloadStream(connectionConfig)
    }

    @Override
    String downloadText() {
        downloadSupport.downloadText()
    }

    @Override
    String downloadText(Map options) {
        downloadSupport.downloadText(options)
    }

    @Override
    String downloadText(Map options, Closure connectionConfig) {
        downloadSupport.downloadText(options, connectionConfig)
    }

    @Override
    String downloadText(String uri) {
        downloadSupport.downloadText(uri)
    }

    @Override
    String downloadText(String uri, Closure connectionConfig) {
        downloadSupport.downloadText(uri, connectionConfig)
    }

    @Override
    String downloadText(Closure connectionConfig) {
        downloadSupport.downloadText(connectionConfig)
    }

    @Override
    byte[] downloadBytes() {
        downloadSupport.downloadBytes()
    }

    @Override
    byte[] downloadBytes(Map options) {
        downloadSupport.downloadBytes(options)
    }

    @Override
    byte[] downloadBytes(Map options, Closure connectionConfig) {
        downloadSupport.downloadBytes(options, connectionConfig)
    }

    @Override
    byte[] downloadBytes(Closure connectionConfig) {
        downloadSupport.downloadBytes(connectionConfig)
    }

    @Override
    byte[] downloadBytes(String uri) {
        downloadSupport.downloadBytes(uri)
    }

    @Override
    byte[] downloadBytes(String uri, Closure connectionConfig) {
        downloadSupport.downloadBytes(uri, connectionConfig)
    }

    @Override
    Object downloadContent() {
        downloadSupport.downloadContent()
    }

    @Override
    Object downloadContent(Map options) {
        downloadSupport.downloadContent(options)
    }

    @Override
    Object downloadContent(Map options, Closure connectionConfig) {
        downloadSupport.downloadContent(options, connectionConfig)
    }

    @Override
    Object downloadContent(String uri) {
        downloadSupport.downloadContent(uri)
    }

    @Override
    Object downloadContent(String uri, Closure connectionConfig) {
        downloadSupport.downloadContent(uri, connectionConfig)
    }

    @Override
    Object downloadContent(Closure connectionConfig) {
        downloadSupport.downloadContent(connectionConfig)
    }
}