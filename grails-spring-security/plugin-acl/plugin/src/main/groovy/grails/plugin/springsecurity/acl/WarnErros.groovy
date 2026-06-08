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

package grails.plugin.springsecurity.acl

import groovy.transform.CompileDynamic
import org.grails.datastore.gorm.GormValidateable
import org.springframework.context.MessageSource
import org.springframework.context.i18n.LocaleContextHolder

trait WarnErros {

    @CompileDynamic
    String errorsBeanBeingSaved(MessageSource messageSource, GormValidateable bean) {
        StringBuilder message = new StringBuilder("problem creating ${bean.getClass().simpleName}: $bean")
        Locale locale = LocaleContextHolder.getLocale()
        for (fieldErrors in bean.errors) {
            for (error in fieldErrors.allErrors) {
                message << '\n\t' << messageSource.getMessage(error, locale)
            }
        }
        message.toString()
    }
}