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
package grails.validation

class TestClass {
    private Object[] testArray
    private BigDecimal testBigDecimal
    private Collection testCollection
    private String testString
    private Date testDate
    private Double testDouble
    private String testEmail
    private Float testFloat
    private Integer testInteger
    private Long testLong
    private String testURL

    Object[] getTestArray() {
        return testArray
    }

    void setTestArray(Object[] testArray) {
        this.testArray = testArray
    }

    BigDecimal getTestBigDecimal() {
        return testBigDecimal
    }

    void setTestBigDecimal(BigDecimal testBigDecimal) {
        this.testBigDecimal = testBigDecimal
    }

    Collection getTestCollection() {
        return testCollection
    }

    void setTestCollection(Collection testCollection) {
        this.testCollection = testCollection
    }

    Date getTestDate() {
        return testDate
    }

    void setTestDate(Date testDate) {
        this.testDate = testDate
    }

    Double getTestDouble() {
        return testDouble
    }

    void setTestDouble(Double testDouble) {
        this.testDouble = testDouble
    }

    String getTestEmail() {
        return testEmail
    }

    void setTestEmail(String testEmail) {
        this.testEmail = testEmail
    }

    Float getTestFloat() {
        return testFloat
    }

    void setTestFloat(Float testFloat) {
        this.testFloat = testFloat
    }

    Integer getTestInteger() {
        return testInteger
    }

    void setTestInteger(Integer testInteger) {
        this.testInteger = testInteger
    }

    String getTestURL() {
        return testURL
    }

    void setTestURL(String testURL) {
        this.testURL = testURL
    }

    String getTestString() {
        return testString
    }

    void setTestString(String testString) {
        this.testString = testString
    }

    Long getTestLong() {
        return testLong
    }

    void setTestLong(Long testLong) {
        this.testLong = testLong
    }
}
