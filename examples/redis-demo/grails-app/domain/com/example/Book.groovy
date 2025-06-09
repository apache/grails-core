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

package com.example

import grails.plugins.redis.RedisService
import groovy.transform.ToString
import java.time.LocalDate

@ToString(includes = "id,createDate")
class Book {

    RedisService redisService

    String title = ''
    LocalDate createDate = LocalDate.now()
    static transients = ['redisService']

    static mapping = {
        autowire true
    }

    //todo: FIX THESE ASAP!
//    @Memoize(key = '#{title}')
    def getMemoizedTitle(LocalDate date) {
        redisService?.memoize(title) {
            println 'cache miss'
            "$title $date"
       }
    }
}