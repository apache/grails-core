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
 * Temporary fork of groovy JsonGenerator until Groovy 2.5.0 is out.
 *
 * <p>Generates JSON from objects.
 *
 * <p>The {@link Options} builder can be used to configure an instance of a JsonGenerator.
 *
 * @see Options#build()
 * @since 2.5
 * @deprecated Use {@link groovy.json.JsonGenerator} instead.
 */
@Deprecated(since = "7.1", forRemoval = true)
public interface JsonGenerator extends groovy.json.JsonGenerator {

    /**
     * Handles converting a given type.
     *
     * @since 2.5
     * @deprecated Use {@link groovy.json.JsonGenerator.Converter} instead.
     */
    @Deprecated(since = "7.1", forRemoval = true)
    interface Converter extends groovy.json.JsonGenerator.Converter {

    }

    /**
     * A builder used to construct a {@link JsonGenerator} instance that allows
     * control over the serialized JSON output.  If you do not need to customize the
     * output it is recommended to use the static {@code JsonOutput.toJson} methods.
     *
     * <p>
     * Example:
     * <pre><code class="groovyTestCase">
     *     def generator = new groovy.json.JsonGenerator.Options()
     *                         .excludeNulls()
     *                         .dateFormat('yyyy')
     *                         .excludeFieldsByName('bar', 'baz')
     *                         .excludeFieldsByType(java.sql.Date)
     *                         .build()
     *
     *     def input = [foo: null, lastUpdated: Date.parse('yyyy-MM-dd', '2014-10-24'),
     *                   bar: 'foo', baz: 'foo', systemDate: new java.sql.Date(new Date().getTime())]
     *
     *     assert generator.toJson(input) == '{"lastUpdated":"2014"}'
     * </code></pre>
     *
     * @since 2.5
     * @deprecated Use {@link groovy.json.JsonGenerator.Options} instead.
     */
    @Deprecated(since = "7.1", forRemoval = true)
    class Options extends groovy.json.JsonGenerator.Options {

    }
}
