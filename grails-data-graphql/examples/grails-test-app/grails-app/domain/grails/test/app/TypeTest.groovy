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

package grails.test.app

import java.sql.Time
import java.sql.Timestamp
import java.time.*

class TypeTest {

    Integer integer
    Long aLong
    Short aShort
    Byte aByte
    Double aDouble
    Float aFloat
    BigInteger bigInteger
    BigDecimal bigDecimal
    String string
    Boolean aBoolean
    Character character
    UUID uuid
    URL url
    URI uri
    Date date
    Byte[] bytes
    Character[] characters
    Time time
    java.sql.Date sqlDate
    Timestamp timestamp
    Currency currency
    TimeZone timeZone

    LocalDate localDate
    LocalDateTime localDateTime
    LocalTime localTime
    OffsetDateTime offsetDateTime
    OffsetTime offsetTime
    ZonedDateTime zonedDateTime
    Instant instant

    char[] charsPrimitive
    byte[] bytesPrimitive
    int intPrimitive
    long longPrimitive
    short shortPrimitive
    byte bytePrimitive
    double doublePrimitive
    float floatPrimitive
    char charPrimitive
    boolean booleanPrimitive

    static constraints = {
    }

    static graphql = true
}
