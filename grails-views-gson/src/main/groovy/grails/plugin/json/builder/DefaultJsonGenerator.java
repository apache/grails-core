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
package grails.plugin.json.builder;

/**
 * Temporary fork of DefaultJsonGenerator until Groovy 2.5.0 is out.
 * <p>A JsonGenerator that can be configured with various {@link JsonGenerator.Options}.
 * If the default options are sufficient consider using the static {@code JsonOutput.toJson}
 * methods.
 *
 * @see JsonGenerator.Options#build()
 * @since 2.5
 * @deprecated Use {@link groovy.json.DefaultJsonGenerator} instead.
 */
@Deprecated(since = "7.1", forRemoval = true)
public class DefaultJsonGenerator extends groovy.json.DefaultJsonGenerator {

    protected DefaultJsonGenerator(groovy.json.DefaultJsonGenerator.Options options) {
        super(options);
    }
}
