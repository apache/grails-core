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
package org.grails.web.servlet.mvc;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import org.jspecify.annotations.NonNull;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

/**
 * Preserves the outer servlet request wrapper while exposing multipart data from
 * the resolved multipart request.
 *
 * @since 8.0
 */
final class GrailsMultipartHttpServletRequest extends HttpServletRequestWrapper
        implements MultipartHttpServletRequest {

    private final MultipartHttpServletRequest multipartRequest;

    GrailsMultipartHttpServletRequest(
            HttpServletRequest request, MultipartHttpServletRequest multipartRequest) {
        super(request);
        this.multipartRequest = multipartRequest;
    }

    @NonNull
    @Override
    public HttpMethod getRequestMethod() {
        return HttpMethod.valueOf(getMethod());
    }

    @NonNull
    @Override
    public HttpHeaders getRequestHeaders() {
        return multipartRequest.getRequestHeaders();
    }

    @NonNull
    @Override
    public Iterator<String> getFileNames() {
        return multipartRequest.getFileNames();
    }

    @Override
    public MultipartFile getFile(@NonNull String name) {
        return multipartRequest.getFile(name);
    }

    @NonNull
    @Override
    public List<MultipartFile> getFiles(@NonNull String name) {
        return multipartRequest.getFiles(name);
    }

    @NonNull
    @Override
    public Map<String, MultipartFile> getFileMap() {
        return multipartRequest.getFileMap();
    }

    @NonNull
    @Override
    public MultiValueMap<String, MultipartFile> getMultiFileMap() {
        return multipartRequest.getMultiFileMap();
    }

    @Override
    public String getMultipartContentType(@NonNull String name) {
        return multipartRequest.getMultipartContentType(name);
    }

    @Override
    public HttpHeaders getMultipartHeaders(@NonNull String name) {
        return multipartRequest.getMultipartHeaders(name);
    }
}
