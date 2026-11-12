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
package openapiapp

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.Schema

/**
 * A converter of the application's own, which records on each schema what it found required, so
 * the document shows what the converter saw.
 */
class RequiredRecordingConverter implements ModelConverter {

    private static final String REFERENCE_PREFIX = '#/components/schemas/'

    @Override
    Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        Schema resolved = chain.hasNext() ? chain.next().resolve(type, context, chain) : null
        Schema model = resolved?.$ref?.startsWith(REFERENCE_PREFIX)
                ? context.definedModels[resolved.$ref.substring(REFERENCE_PREFIX.length())]
                : resolved
        if (model?.required) {
            model.addExtension('x-required-seen', new ArrayList<String>(model.required))
        }
        resolved
    }
}
