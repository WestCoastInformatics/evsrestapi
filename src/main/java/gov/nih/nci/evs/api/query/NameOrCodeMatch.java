package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches when either the name field or the code field matches the value match, e.g. a relationship
 * entry whose type name or type code is "R101". Combined with a raw bool query until the logical
 * operator nodes land, when it becomes an {@code Or} of two {@link FieldMatch}es.
 */
final class NameOrCodeMatch extends EvsQuery {

  /** Either the name or the code matching is enough. */
  private static final int MINIMUM_MATCHING_FIELDS = 1;

  /** The field holding the name. */
  private String nameField;

  /** The field holding the code. */
  private String codeField;

  /** How the name or code must match. */
  private ValueMatch nameOrCodeMatch;

  /** Instantiates an empty {@link NameOrCodeMatch}. */
  NameOrCodeMatch() {
    // n/a
  }

  /**
   * Instantiates a {@link NameOrCodeMatch}.
   *
   * @param nameField the field holding the name
   * @param codeField the field holding the code
   * @param nameOrCodeMatch how the name or code must match
   */
  NameOrCodeMatch(
      final String nameField, final String codeField, final ValueMatch nameOrCodeMatch) {
    this.nameField = Objects.requireNonNull(nameField, "nameField");
    this.codeField = Objects.requireNonNull(codeField, "codeField");
    this.nameOrCodeMatch = Objects.requireNonNull(nameOrCodeMatch, "nameOrCodeMatch");
  }

  /**
   * Returns the field holding the name.
   *
   * @return the name field
   */
  public String getNameField() {
    return nameField;
  }

  /**
   * Sets the field holding the name.
   *
   * @param nameField the name field
   */
  public void setNameField(final String nameField) {
    this.nameField = nameField;
  }

  /**
   * Returns the field holding the code.
   *
   * @return the code field
   */
  public String getCodeField() {
    return codeField;
  }

  /**
   * Sets the field holding the code.
   *
   * @param codeField the code field
   */
  public void setCodeField(final String codeField) {
    this.codeField = codeField;
  }

  /**
   * Returns how the name or code must match.
   *
   * @return the name or code match
   */
  public ValueMatch getNameOrCodeMatch() {
    return nameOrCodeMatch;
  }

  /**
   * Sets how the name or code must match.
   *
   * @param nameOrCodeMatch the name or code match
   */
  public void setNameOrCodeMatch(final ValueMatch nameOrCodeMatch) {
    this.nameOrCodeMatch = nameOrCodeMatch;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(
      final OpenSearchOperations operations, final List<Terminology> terminologies) {
    final FieldMatch nameMatch = new FieldMatch(nameField, nameOrCodeMatch);
    final FieldMatch codeMatch = new FieldMatch(codeField, nameOrCodeMatch);

    return QueryBuilders.boolQuery()
        .should(nameMatch.fetchOpenSearchQuery(operations, terminologies))
        .should(codeMatch.fetchOpenSearchQuery(operations, terminologies))
        .minimumShouldMatch(MINIMUM_MATCHING_FIELDS);
  }
}
