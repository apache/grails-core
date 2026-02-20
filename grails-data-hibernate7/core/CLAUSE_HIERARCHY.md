# Criteria and Projection Hierarchy in PredicateGenerator

| Class used in `instanceof` | Class Hierarchy (from `Query.java` or GORM) |
| :--- | :--- |
| `Query.Junction` | `Query.Junction` -> `Query.Criterion` |
| `Query.Disjunction` | `Query.Disjunction` -> `Query.Junction` -> `Query.Criterion` |
| `Query.Conjunction` | `Query.Conjunction` -> `Query.Junction` -> `Query.Criterion` |
| `Query.Negation` | `Query.Negation` -> `Query.Junction` -> `Query.Criterion` |
| `Query.DistinctProjection` | `Query.DistinctProjection` -> `Query.Projection` |
| `DetachedAssociationCriteria` | `DetachedAssociationCriteria` -> `AbstractDetachedCriteria` -> `Criteria` |
| `Query.PropertyCriterion` | `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.Equals` | `Query.Equals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.NotEquals` | `Query.NotEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.IdEquals` | `Query.IdEquals` -> `Query.Criterion` |
| `Query.GreaterThan` | `Query.GreaterThan` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanEquals` | `Query.GreaterThanEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThan` | `Query.LessThan` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanEquals` | `Query.LessThanEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SizeEquals` | `Query.SizeEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SizeNotEquals` | `Query.SizeNotEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SizeGreaterThan` | `Query.SizeGreaterThan` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SizeGreaterThanEquals` | `Query.SizeGreaterThanEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SizeLessThan` | `Query.SizeLessThan` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SizeLessThanEquals` | `Query.SizeLessThanEquals` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.Between` | `Query.Between` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.ILike` | `Query.ILike` -> `Query.Like` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.RLike` | `Query.RLike` -> `Query.Like` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.Like` | `Query.Like` -> `Query.PropertyCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.In` | `Query.In` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.NotIn` | `Query.NotIn` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.SubqueryCriterion` | `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanEqualsAll` | `Query.GreaterThanEqualsAll` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanAll` | `Query.GreaterThanAll` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanEqualsAll` | `Query.LessThanEqualsAll` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanAll` | `Query.LessThanAll` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.EqualsAll` | `Query.EqualsAll` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanEqualsSome` | `Query.GreaterThanEqualsSome` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanSome` | `Query.GreaterThanSome` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanEqualsSome` | `Query.LessThanEqualsSome` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanSome` | `Query.LessThanSome` -> `Query.SubqueryCriterion` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.IsNull` | `Query.IsNull` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.IsNotNull` | `Query.IsNotNull` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.IsEmpty` | `Query.IsEmpty` -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.IsNotEmpty` | `Query.IsNotEmpty` -> `Query.PropertyNameCriterion" -> `Query.Criterion` |
| `Query.EqualsProperty` | `Query.EqualsProperty` -> `Query.PropertyComparisonCriterion" -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.NotEqualsProperty` | `Query.NotEqualsProperty` -> `Query.PropertyComparisonCriterion" -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanEqualsProperty` | `Query.LessThanEqualsProperty` -> `Query.PropertyComparisonCriterion" -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.LessThanProperty` | `Query.LessThanProperty` -> `Query.PropertyComparisonCriterion" -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanEqualsProperty` | `Query.GreaterThanEqualsProperty" -> `Query.PropertyComparisonCriterion" -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.GreaterThanProperty` | `Query.GreaterThanProperty" -> `Query.PropertyComparisonCriterion" -> `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.Exists` | `Query.Exists` -> `Query.Criterion` |
| `Query.NotExists` | `Query.NotExists` -> `Query.Criterion` |
| `Query.PropertyNameCriterion` | `Query.PropertyNameCriterion` -> `Query.Criterion` |
| `Query.PropertyProjection` | `Query.PropertyProjection` -> `Query.Projection` |
| `Query.DistinctPropertyProjection` | `Query.DistinctPropertyProjection` -> `Query.PropertyProjection` -> `Query.Projection` |
| `Query.IdProjection` | `Query.IdProjection` -> `Query.Projection` |
| `Query.CountProjection` | `Query.CountProjection` -> `Query.Projection` |
| `Query.CountDistinctProjection` | `Query.CountDistinctProjection` -> `Query.PropertyProjection` -> `Query.Projection` |
| `Query.MaxProjection` | `Query.MaxProjection` -> `Query.PropertyProjection` -> `Query.Projection` |
| `Query.MinProjection` | `Query.MinProjection` -> `Query.PropertyProjection` -> `Query.Projection` |
| `Query.AvgProjection` | `Query.AvgProjection` -> `Query.PropertyProjection` -> `Query.Projection` |
| `Query.SumProjection` | `Query.SumProjection` -> `Query.PropertyProjection` -> `Query.Projection` |
