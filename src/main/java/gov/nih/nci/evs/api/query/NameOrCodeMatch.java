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
  private final String nameField;

  /** The field holding the code. */
  private final String codeField;

  /** How the name or code must match. */
  private final ValueMatch nameOrCodeMatch;

  /**
   * Instantiates a {@link NameOrCodeMatch}.
   *
   * @param terminologies the terminologies being searched
   * @param nameField the field holding the name
   * @param codeField the field holding the code
   * @param nameOrCodeMatch how the name or code must match
   */
  NameOrCodeMatch(
      final List<Terminology> terminologies,
      final String nameField,
      final String codeField,
      final ValueMatch nameOrCodeMatch) {
    super(terminologies);
    this.nameField = Objects.requireNonNull(nameField, "nameField");
    this.codeField = Objects.requireNonNull(codeField, "codeField");
    this.nameOrCodeMatch = Objects.requireNonNull(nameOrCodeMatch, "nameOrCodeMatch");
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final FieldMatch nameMatch = new FieldMatch(terminologies, nameField, nameOrCodeMatch);
    final FieldMatch codeMatch = new FieldMatch(terminologies, codeField, nameOrCodeMatch);

    return QueryBuilders.boolQuery()
        .should(nameMatch.fetchOpenSearchQuery(operations))
        .should(codeMatch.fetchOpenSearchQuery(operations))
        .minimumShouldMatch(MINIMUM_MATCHING_FIELDS);
  }
}
