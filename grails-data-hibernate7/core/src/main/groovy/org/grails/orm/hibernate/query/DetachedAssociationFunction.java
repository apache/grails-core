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
package org.grails.orm.hibernate.query;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria;
import org.grails.datastore.mapping.query.Query;

@SuppressWarnings({"unchecked", "rawtypes"})
public class DetachedAssociationFunction
    implements Function<Query.Criterion, List<DetachedAssociationCriteria<?>>> {
  @Override
  public List<DetachedAssociationCriteria<?>> apply(Query.Criterion o) {
    List<Query.Criterion> criteria;
    if (o instanceof Query.In c && Objects.nonNull(c.getSubquery())) {
      criteria = c.getSubquery().getCriteria();
    } else if (o instanceof Query.Exists c && Objects.nonNull(c.getSubquery())) {
      criteria = c.getSubquery().getCriteria();
    } else if (o instanceof Query.NotExists c && Objects.nonNull(c.getSubquery())) {
      criteria = c.getSubquery().getCriteria();
    } else if (o instanceof Query.SubqueryCriterion c && Objects.nonNull(c.getValue())) {
      criteria = c.getValue().getCriteria();
    } else {
      criteria = List.of(o);
    }
    return criteria.stream()
        .filter(it -> it instanceof DetachedAssociationCriteria)
        .map(it -> (DetachedAssociationCriteria<?>) it)
        .collect(java.util.stream.Collectors.toList());
  }
}
