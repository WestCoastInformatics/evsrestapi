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
 * The leaf {@link QualifierCondition}: a qualifier a property must have for {@link HasProperty} to
 * match, e.g. a GO_Annotation whose go-evi (P389) is "TAS". The qualifier's type and value must
 * match on the same qualifier entry, of the same property entry.
 *
 * <p>Built into the property's query rather than run as a separate search: qualifiers are in the
 * concept's own document, and a separate search would only say which concepts have the qualifier,
 * not which property it's on.
 */
public final class PropertyQualifier implements QualifierCondition {

  /** A qualifier entry's type name, e.g. "go-evi", under the qualifier list field. */
  private static final String TYPE_NAME_FIELD = "type";

  /** A qualifier entry's type code, e.g. "P389", under the qualifier list field. */
  private static final String TYPE_CODE_FIELD = "code";

  /** A qualifier entry's value, e.g. "TAS", under the qualifier list field. */
  private static final String VALUE_FIELD = "value";

  /** Separates the qualifier list field from a qualifier entry's field. */
  private static final String ENTRY_FIELD_SEPARATOR = ".";

  private ValueMatch qualifier;

  /** The match for the qualifier's value, or null to match any value. */
  private ValueMatch value;

  /** Instantiates an empty {@link PropertyQualifier}. */
  public PropertyQualifier() {
    // n/a
  }

  /**
   * Instantiates a {@link PropertyQualifier} that matches a qualifier of the given type, with any
   * value.
   *
   * @param qualifier the match for the qualifier's type name or code
   */
  public PropertyQualifier(final ValueMatch qualifier) {
    this.qualifier = Objects.requireNonNull(qualifier, "qualifier");
    this.value = null;
  }

  /**
   * Instantiates a {@link PropertyQualifier} that matches a qualifier of the given type with a
   * matching value.
   *
   * @param qualifier the match for the qualifier's type name or code
   * @param value the match for the qualifier's value
   */
  public PropertyQualifier(final ValueMatch qualifier, final ValueMatch value) {
    this.qualifier = Objects.requireNonNull(qualifier, "qualifier");
    this.value = Objects.requireNonNull(value, "value");
  }

  /**
   * Returns the match for the qualifier's type name or code.
   *
   * @return the type name or code match
   */
  public ValueMatch getQualifier() {
    return qualifier;
  }

  /**
   * Sets the match for the qualifier's type name or code.
   *
   * @param qualifier the type name or code match
   */
  public void setQualifier(final ValueMatch qualifier) {
    this.qualifier = qualifier;
  }

  /**
   * Returns the match for the qualifier's value.
   *
   * @return the value match, or null to match any value
   */
  public ValueMatch getValue() {
    return value;
  }

  /**
   * Sets the match for the qualifier's value.
   *
   * @param value the value match, or null to match any value
   */
  public void setValue(final ValueMatch value) {
    this.value = value;
  }

  /**
   * Returns a query matching property entries with at least one qualifier entry matching this
   * qualifier. Runs inside the query over the property entries.
   *
   * @param qualifierListField the property entry's list of qualifiers, mapped as nested
   * @param operations the OpenSearch operations
   * @param terminologies the terminologies being searched
   * @return the query
   */
  @Override
  public QueryBuilder getPropertiesMatching(
      final String qualifierListField,
      final OpenSearchOperations operations,
      final List<Terminology> terminologies) {
    final NameOrCodeMatch typeMatch =
        new NameOrCodeMatch(
            getEntryFieldName(qualifierListField, TYPE_NAME_FIELD),
            getEntryFieldName(qualifierListField, TYPE_CODE_FIELD),
            qualifier);
    final BoolQueryBuilder entryConditions =
        QueryBuilders.boolQuery().must(typeMatch.fetchOpenSearchQuery(operations, terminologies));

    final boolean hasValueMatch = value != null;
    if (hasValueMatch) {
      final FieldMatch qualifierValueMatch =
          new FieldMatch(getEntryFieldName(qualifierListField, VALUE_FIELD), value);
      entryConditions.must(qualifierValueMatch.fetchOpenSearchQuery(operations, terminologies));
    }

    return QueryBuilders.nestedQuery(qualifierListField, entryConditions, ScoreMode.None);
  }

  /**
   * Returns the full name of a qualifier entry's field.
   *
   * @param qualifierListField the property entry's list of qualifiers
   * @param entryField the field within a qualifier entry
   * @return the field name
   */
  private static String getEntryFieldName(
      final String qualifierListField, final String entryField) {
    return qualifierListField + ENTRY_FIELD_SEPARATOR + entryField;
  }
}
