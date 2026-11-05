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

  private ValueMatch property;

  /** The match for the property's value, or null to match any value. */
  private ValueMatch value;

  /** The condition the property's qualifiers must meet, or null to require none. */
  private QualifierCondition qualifiers;

  /** Instantiates an empty {@link HasProperty}. */
  public HasProperty() {
    // n/a
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type, with any value.
   *
   * @param property the match for the property's type name or code
   */
  public HasProperty(final ValueMatch property) {
    this(property, null, null);
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type with a matching
   * value.
   *
   * @param property the match for the property's type name or code
   * @param value the match for the property's value
   */
  public HasProperty(final ValueMatch property, final ValueMatch value) {
    this(property, Objects.requireNonNull(value, "value"), null);
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type, with any value,
   * whose qualifiers meet the condition.
   *
   * @param property the match for the property's type name or code
   * @param qualifiers the condition the property's qualifiers must meet
   */
  public HasProperty(final ValueMatch property, final QualifierCondition qualifiers) {
    this(property, null, Objects.requireNonNull(qualifiers, "qualifiers"));
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type with a matching
   * value, whose qualifiers meet the condition.
   *
   * @param property the match for the property's type name or code
   * @param value the match for the property's value, or null for any value
   * @param qualifiers the condition the property's qualifiers must meet, or null for none
   */
  public HasProperty(
      final ValueMatch property, final ValueMatch value, final QualifierCondition qualifiers) {
    this.property = Objects.requireNonNull(property, "property");
    this.value = value;
    this.qualifiers = qualifiers;
  }

  /**
   * Returns the match for the property's type name or code.
   *
   * @return the type name or code match
   */
  public ValueMatch getProperty() {
    return property;
  }

  /**
   * Sets the match for the property's type name or code.
   *
   * @param property the type name or code match
   */
  public void setProperty(final ValueMatch property) {
    this.property = property;
  }

  /**
   * Returns the match for the property's value.
   *
   * @return the value match, or null to match any value
   */
  public ValueMatch getValue() {
    return value;
  }

  /**
   * Sets the match for the property's value.
   *
   * @param value the value match, or null to match any value
   */
  public void setValue(final ValueMatch value) {
    this.value = value;
  }

  /**
   * Returns the condition the property's qualifiers must meet.
   *
   * @return the qualifier condition, or null to require none
   */
  public QualifierCondition getQualifiers() {
    return qualifiers;
  }

  /**
   * Sets the condition the property's qualifiers must meet.
   *
   * @param qualifiers the qualifier condition, or null to require none
   */
  public void setQualifiers(final QualifierCondition qualifiers) {
    this.qualifiers = qualifiers;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(
      final OpenSearchOperations operations, final List<Terminology> terminologies) {
    final NameOrCodeMatch typeMatch =
        new NameOrCodeMatch(TYPE_NAME_FIELD, TYPE_CODE_FIELD, property);
    final BoolQueryBuilder entryConditions =
        QueryBuilders.boolQuery().must(typeMatch.fetchOpenSearchQuery(operations, terminologies));

    final boolean hasValueMatch = value != null;
    if (hasValueMatch) {
      final FieldMatch propertyValueMatch = new FieldMatch(VALUE_FIELD, value);
      entryConditions.must(propertyValueMatch.fetchOpenSearchQuery(operations, terminologies));
    }

    final boolean hasQualifierCondition = qualifiers != null;
    if (hasQualifierCondition) {
      entryConditions.must(
          qualifiers.getPropertiesMatching(QUALIFIERS_FIELD, operations, terminologies));
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
