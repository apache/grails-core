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

import grails.plugins.redis.*
import java.time.LocalDate

class BookService {

    RedisService redisService

    @MemoizeScore(key = '#{map.key}', member = 'foo')
    def getAnnotatedScore(Map map) {
        println 'cache miss getAnnotatedScore'
        return map.foo
    }

    @MemoizeList(key = '#{list[0]}')
    def getAnnotatedList(List list) {
        println 'cache miss getAnnotatedList'
        return list
    }

    @MemoizeHash(key = '#{map.foo}')
    def getAnnotatedHash(Map map) {
        println 'cache miss getAnnotatedHash'
        return map
    }

    @MemoizeHashField(key = '#{map.foo}', member = 'foo')
    def getAnnotatedHashField(Map map) {
        println 'cache miss getAnnotatedHashField'
        return map.foo
    }


    @MemoizeDomainObject(key = '#{title}', clazz = Book.class)
    def createDomainObject(String title, LocalDate date) {
        println 'cache miss createDomainObject'
        def book = new Book(title: title, createDate: date).save(flush: true)
        book
    }


    @MemoizeDomainList(key = 'getDomainListWithKeyClass:#{title}', clazz = Book.class)
    def getDomainListWithKeyClass(String title, Date date) {
        redisService.domainListWithKeyClassKey = "$title $date"
        println 'cache miss getDomainListWithKeyClass'
        Book.executeQuery("from Book b where b.title = :title", [title: title])
    }

    @Memoize({ '#{text}' })
    def getAnnotatedTextUsingClosure(String text, Date date) {
        println 'cache miss getAnnotatedTextUsingClosure'
        return "$text $date"
    }

    @Memoize(key = '#{text}')
    def getAnnotatedTextUsingKey(String text, Date date) {
        println 'cache miss getAnnotatedTextUsingKey'
        return "$text $date"
    }

    //expire this extremely fast to test that it works
    @Memoize(key = '#{text}', expire = '1')
    def getAnnotatedTextUsingKeyAndExpire(String text, Date date) {
        println 'cache miss getAnnotatedTextUsingKeyAndExpire'
        return "$text $date"
    }

    @Memoize(key = '#{book.title}:#{book.id}')
    def getAnnotatedBook(Book book) {
        println 'cache miss getAnnotatedBook'
        return book.toString()
    }

    def getMemoizedTextDate(String text, Date date) {
        return redisService.memoize(text) {
            println 'cache miss getMemoizedTextDate'
            return "$text $date"
        }
    }
}