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
import org.springframework.validation.*

/**
 * Renders validation errors according to vnd.error: https://github.com/blongden/vnd.error
 */
model {
    Errors errors
}

xmlDeclaration()
response { status UNPROCESSABLE_ENTITY }

'errors' {
    Errors errorsObject = (Errors)this.errors
    def allErrors = errorsObject.allErrors
    int errorCount = allErrors.size()
    def resourcePath = g.link(resource:request.uri, absolute:false)
    def resourceLink = g.link(resource:request.uri, absolute:true)
    if(errorCount == 1) {
        def error = allErrors.iterator().next()
        'error' {
            message messageSource.getMessage(error, locale)
            path resourcePath
            _links {
                self {
                    href resourceLink
                }
            }
        }
    }
    else {
        total errorCount
        _embedded {
            errors(allErrors) { ObjectError error ->
                message messageSource.getMessage(error, locale)
                path resourcePath
                _links {
                    self {
                        href resourceLink
                    }
                }
            }
        }
    }
}
