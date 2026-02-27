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
package org.grails.orm.hibernate.cfg.domainbinding.util;

import io.micrometer.common.util.StringUtils;
import jakarta.validation.constraints.NotNull;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.MappingException;
import org.hibernate.mapping.Column;
import org.hibernate.mapping.UniqueKey;

public class UniqueNameGenerator {

  private static final int MAX_LENGTH = 30;

  public void setGeneratedUniqueName(@NotNull UniqueKey uk) {
    if (uk.getTable() == null) {
      throw new MappingException(
          String.format("Unique Key %s does not have a table associated with it", uk.getName()));
    }

    try {
      var fields = new ArrayList<>(List.of(uk.getTable().getName()));
      uk.getColumns().stream()
          .map(Column::getName)
          .filter(StringUtils::isNotBlank)
          .forEach(fields::add);
      var ukString = String.join("_", fields);
      MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(ukString.getBytes(StandardCharsets.UTF_8));
      String name = "UK" + new BigInteger(1, md.digest()).toString(16);
      if (name.length() > MAX_LENGTH) {
        name = name.substring(0, MAX_LENGTH);
      }
      uk.setName(name);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
