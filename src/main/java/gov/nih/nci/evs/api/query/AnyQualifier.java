package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches property entries whose qualifiers meet at least one condition. No conditions matches
 * nothing, as for the concept-level {@code Or}, so an empty list of alternatives doesn't widen a
 * search to every property. See {@link QualifierCondition} for why this isn't {@code Or}.
 */
public final class AnyQualifier implements QualifierCondition {

  /** One of the conditions must hold. */
  private static final int MINIMUM_MATCHING_CONDITIONS = 1;

  /** The alternative conditions. Unmodifiable. */
  private final List<QualifierCondition> conditions;

  /**
   * Instantiates an {@link AnyQualifier}.
   *
   * @param conditions the alternative conditions
   */
  public AnyQualifier(final List<QualifierCondition> conditions) {
    this.conditions = List.copyOf(Objects.requireNonNull(conditions, "conditions"));
  }

  /* see superclass */
  @Override
  public QueryBuilder getPropertiesMatching(
      final List<Terminology> terminologies,
      final String qualifierListField,
      final OpenSearchOperations operations) {
    final boolean hasAlternatives = !conditions.isEmpty();
    if (!hasAlternatives) {
      return QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery());
    }

    final BoolQueryBuilder anyConditionQuery =
        QueryBuilders.boolQuery().minimumShouldMatch(MINIMUM_MATCHING_CONDITIONS);
    for (final QualifierCondition condition : conditions) {
      anyConditionQuery.should(
          condition.getPropertiesMatching(terminologies, qualifierListField, operations));
    }
    return anyConditionQuery;
  }
}
