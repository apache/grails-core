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

package functionaltests

import org.grails.exceptions.ExceptionUtils

class ErrorsController {
    def throwErrorInInterceptor() {
        // never hit
        assert false
    }
    def throwCustomError() {
        throw new CustomException("Something bad")
    }
    def throwException() {
        throw new Exception("Oops!")
    }

    def throwGeneralError() {
        throw new IllegalStateException("Something went wrong")
    }

    def customError(CustomException exception) {
        render "<html><body>Message = $exception.message</body><html>"
    }

    def customErrorHandler() {
        def cause = ExceptionUtils.getRootCause(request.exception)
        render "<html><body>Message = $cause.message</body><html>"   
    }

    def notFound() {
    	render "Page Not Found"
    }

    def notFoundTest() {
    	render status:404
    }
}
