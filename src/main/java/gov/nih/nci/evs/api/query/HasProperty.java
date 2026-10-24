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
 * be found with this query.
 *
 * <p>Qualifiers ({@link PropertyQualifier}) must all be on that same property entry, e.g. a
 * GO_Annotation whose go-id is "GO:0007050" and whose go-evi is "TAS". Each qualifier can be a
 * different qualifier entry of the property. Qualifiers remodeled into their own fields, such as a
 * synonym's source, aren't property qualifiers, so they can't be matched here.
 *
 * <p><b>Exploratory design: the qualifier list is an implicit AND.</b> The concept-level {@code
 * And}/{@code Or}/{@code Not} nodes can't combine qualifiers, because combining two {@link
 * HasProperty} nodes only requires each qualifier on some property, not on the same one. So the
 * list is, for now, the only way to require several qualifiers together. It can't express "either
 * qualifier" or "no such qualifier". The likely replacement is a single qualifier condition with
 * its own logical operators, each {@link PropertyQualifier} keeping its own nested query so {@code
 * Not} means "has no qualifier matching". That's undecided, so don't build on the list.
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

  /**
   * The qualifiers the property must all have: an implicit AND (see the class Javadoc).
   * Unmodifiable, and empty to require none.
   */
  private final List<PropertyQualifier> qualifiers;

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type, with any value.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   */
  public HasProperty(final List<Terminology> terminologies, final ValueMatch typeNameOrCodeMatch) {
    this(terminologies, typeNameOrCodeMatch, null, List.of());
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
        terminologies,
        typeNameOrCodeMatch,
        Objects.requireNonNull(valueMatch, "valueMatch"),
        List.of());
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type, with any value,
   * that has all the given qualifiers.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   * @param qualifiers the qualifiers the property must all have
   */
  public HasProperty(
      final List<Terminology> terminologies,
      final ValueMatch typeNameOrCodeMatch,
      final List<PropertyQualifier> qualifiers) {
    this(terminologies, typeNameOrCodeMatch, null, qualifiers);
  }

  /**
   * Instantiates a {@link HasProperty} that matches a property of the given type with a matching
   * value, that has all the given qualifiers.
   *
   * @param terminologies the terminologies being searched
   * @param typeNameOrCodeMatch the match for the property's type name or code
   * @param valueMatch the match for the property's value
   * @param qualifiers the qualifiers the property must all have
   */
  public HasProperty(
      final List<Terminology> terminologies,
      final ValueMatch typeNameOrCodeMatch,
      final ValueMatch valueMatch,
      final List<PropertyQualifier> qualifiers) {
    super(terminologies);
    this.typeNameOrCodeMatch = Objects.requireNonNull(typeNameOrCodeMatch, "typeNameOrCodeMatch");
    this.valueMatch = valueMatch;
    this.qualifiers = List.copyOf(Objects.requireNonNull(qualifiers, "qualifiers"));
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

    addQualifierConditions(entryConditions, operations);

    return getConceptsWithAnyPropertyMatching(entryConditions);
  }

  /**
   * Requires the property entry to have every qualifier, each on any of its qualifier entries.
   *
   * @param entryConditions the conditions one property entry must meet, added to
   * @param operations the OpenSearch operations
   */
  private void addQualifierConditions(
      final BoolQueryBuilder entryConditions, final OpenSearchOperations operations) {
    for (final PropertyQualifier qualifier : qualifiers) {
      entryConditions.must(
          qualifier.getPropertiesWithAnyQualifierMatching(
              terminologies, QUALIFIERS_FIELD, operations));
    }
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
