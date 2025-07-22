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

package org.grails.testing.runtime.support

import grails.web.Action
import groovy.transform.CompileStatic
import javassist.util.proxy.MethodHandler
import org.grails.web.servlet.mvc.GrailsWebRequest

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

@CompileStatic
class ActionSettingMethodHandler implements MethodHandler {

    GrailsWebRequest request
    Object controller

    ActionSettingMethodHandler(Object controller, GrailsWebRequest request) {
        this.request = request
        this.controller = controller
    }

    @Override
    Object invoke(Object self, Method thisMethod, Method proceed, Object[] args) throws Throwable {
        if (thisMethod.getAnnotation(Action) != null) {
            request.setActionName(thisMethod.name)
        }
        try {
            thisMethod.accessible = true
            thisMethod.invoke(controller, args)
        } catch (InvocationTargetException e) {
            throw e.cause ?: e
        }
    }
}
