package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches concepts that have a property of the given type, optionally with a value matching the
 * given match, e.g. concepts whose Neoplastic_Status (P363) is "Malignant". The type and the value
 * must match on the same property entry: a concept with P363 "Benign" and some other property whose
 * value is "Malignant" doesn't match.
 *
 * <p>Only searches the concept's {@code properties}. Properties remodeled into their own lists,
 * such as synonyms (FULL_SYN, P90) and definitions (DEFINITION, P97), aren't there, so they can't
 * be found with this query. Use {@link HasSynonym} for synonyms.
 *
 * <p>The qualifier condition ({@link QualifierCondition}) must hold on that same property entry,
 * e.g. a GO_Annotation whose go-id is "GO:0007050" and whose go-evi is "TAS". Combine qualifiers
 * with {@link AllQualifiers}, {@link AnyQualifier}, and {@link NoQualifier}, not the concept-level
 * logical nodes, which can't keep them on one entry (see {@link QualifierCondition}). Each {@link
 * PropertyQualifier} can be a different qualifier entry of the property. Qualifiers remodeled into
 * their own fields, such as a synonym's source, aren't property qualifiers, so they can't be
 * matched here.
 */
public final class HasProperty extends EvsQuery {

  /** The concept document's list of properties, mapped as nested so properties can be searched. */
  private static final String PROPERTIES_FIELD = "properties";

  /** A property entry's type name, e.g. "Neoplastic_Status". */
  private static final String TYPE_NAME_FIELD = PROPERTIES_FIELD + ".type";

  /** A property entry's type code, e.g. "P363". */
  private static final String TYPE_CODE_FIELD = PROPERTIES_FIELD + ".code";

  /** A property entry's value, e.g. "Malignant". */
  private static final String VALUE_FIELD = PROPERTIES_FIELD + ".value";

  /** A property entry's list of qualifiers, mapped as nested so qualifiers can be searched. */
  private static final String QUALIFIERS_FIELD = PROPERTIES_FIELD + ".qualifiers";

  private final ValueMatch typeNameOrCodeMatch;

  /** The match for the property's value, or null to match any value. */
  private final ValueMatch valueMatch;

  /** The condition the property's qualifiers must meet, or null to require none. */
  private final QualifierCondition qualifierCondition;

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type, with any value.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   */
  public HasProperty(final List<Terminology> terminologies, final ValueMatch typeNameOrCodeMatch) {
    this(terminologies, typeNameOrCodeMatch, null, null);
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type with a matching
   * value.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   * @param valueMatch the match for the property's value
   */
  public HasProperty(
      final List<Terminology> terminologies,
      final ValueMatch typeNameOrCodeMatch,
      final ValueMatch valueMatch) {
    this(
        terminologies, typeNameOrCodeMatch, Objects.requireNonNull(valueMatch, "valueMatch"), null);
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type, with any value,
   * whose qualifiers meet the condition.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   * @param qualifierCondition the condition the property's qualifiers must meet
   */
  public HasProperty(
      final List<Terminology> terminologies,
      final ValueMatch typeNameOrCodeMatch,
      final QualifierCondition qualifierCondition) {
    this(
        terminologies,
        typeNameOrCodeMatch,
        null,
        Objects.requireNonNull(qualifierCondition, "qualifierCondition"));
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type with a matching
   * value, whose qualifiers meet the condition.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   * @param valueMatch the match for the property's value, or null for any value
   * @param qualifierCondition the condition the property's qualifiers must meet, or null for none
   */
  public HasProperty(
      final List<Terminology> terminologies,
      final ValueMatch typeNameOrCodeMatch,
      final ValueMatch valueMatch,
      final QualifierCondition qualifierCondition) {
    super(terminologies);
    this.typeNameOrCodeMatch = Objects.requireNonNull(typeNameOrCodeMatch, "typeNameOrCodeMatch");
    this.valueMatch = valueMatch;
    this.qualifierCondition = qualifierCondition;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final NameOrCodeMatch typeMatch =
        new NameOrCodeMatch(terminologies, TYPE_NAME_FIELD, TYPE_CODE_FIELD, typeNameOrCodeMatch);
    final BoolQueryBuilder entryConditions =
        QueryBuilders.boolQuery().must(typeMatch.fetchOpenSearchQuery(operations));

    final boolean hasValueMatch = valueMatch != null;
    if (hasValueMatch) {
      final FieldMatch propertyValueMatch = new FieldMatch(terminologies, VALUE_FIELD, valueMatch);
      entryConditions.must(propertyValueMatch.fetchOpenSearchQuery(operations));
    }

    final boolean hasQualifierCondition = qualifierCondition != null;
    if (hasQualifierCondition) {
      entryConditions.must(
          qualifierCondition.getPropertiesMatching(terminologies, QUALIFIERS_FIELD, operations));
    }

    return getConceptsWithAnyPropertyMatching(entryConditions);
  }

  /**
   * Returns a query matching concepts with at least one property entry meeting the conditions.
   *
   * @param entryConditions the conditions one property entry must meet
   * @return the query
   */
  private static QueryBuilder getConceptsWithAnyPropertyMatching(
      final QueryBuilder entryConditions) {
    return QueryBuilders.nestedQuery(PROPERTIES_FIELD, entryConditions, ScoreMode.None);
  }
}
