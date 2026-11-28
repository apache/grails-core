%{--
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
--}%
import org.springframework.validation.*

/**
 * Renders validation errors according to vnd.error: https://github.com/blongden/vnd.error
 */
model {
    Errors errors
}

xmlDeclaration()
this.response.status 422
'errors' {
    Errors errorsObject = (Errors)this.errors
    List<ObjectError> allErrors = errorsObject.allErrors
    int errorCount = allErrors.size()
    String resourcePath = this.g.link(resource:this.request.uri, absolute:false)
    String resourceLink = this.g.link(resource:this.request.uri, absolute:true)
    if(errorCount == 1) {
        ObjectError only = allErrors.iterator().next()
        'error' {
            message this.messageSource.getMessage(only, this.locale)
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
            allErrors.each { ObjectError each ->
                'error' {
                    message this.messageSource.getMessage(each, this.locale)
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
}
