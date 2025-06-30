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
package org.grails.forge.client.github.v3;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.micronaut.core.annotation.Introspected;

/**
 * @author Pavol Gressa
 * @since 6.0.0
 */
@Introspected
public class GitHubSecret {
    private final String encryptedValue;
    private final String keyId;

    @JsonCreator
    public GitHubSecret(@JsonProperty("encrypted_value") String encryptedValue,
                        @JsonProperty("key_id") String keyId) {
        this.encryptedValue = encryptedValue;
        this.keyId = keyId;
    }

    @JsonProperty("encrypted_value")
    public String getEncryptedValue() {
        return encryptedValue;
    }

    @JsonProperty("key_id")
    public String getKeyId() {
        return keyId;
    }

    @Override
    public String toString() {
        return "GitHubSecret{" +
                "encryptedValue='" + encryptedValue + '\'' +
                ", keyId='" + keyId + '\'' +
                '}';
    }
}
