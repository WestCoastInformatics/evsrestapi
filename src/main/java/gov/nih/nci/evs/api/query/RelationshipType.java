package gov.nih.nci.evs.api.query;

import java.util.Objects;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;

/**
 * The type of relationship a {@link HasRelationship} looks for, e.g.
 * "Disease_Has_Primary_Anatomic_Site" (code "R101"). An entry matches when either its type name or
 * its type code matches the {@link ValueMatch}, so the caller picks how the type is matched without
 * naming the entry's fields: {@code new RelationshipType(new StartsWith("Disease_Has_Primary"))}.
 * Wrap the match in {@link CaseInsensitive} to ignore case.
 */
public final class RelationshipType {

  /** The entry field holding the type's name, e.g. "Disease_Has_Primary_Anatomic_Site". */
  private static final String TYPE_NAME_FIELD = "type";

  /** The entry field holding the type's code, e.g. "R101". */
  private static final String TYPE_CODE_FIELD = "code";

  /** How the entry's type name or code must match. */
  private final ValueMatch nameOrCodeMatch;

  /**
   * Instantiates a {@link RelationshipType}.
   *
   * @param nameOrCodeMatch how the entry's type name or code must match
   */
  public RelationshipType(final ValueMatch nameOrCodeMatch) {
    this.nameOrCodeMatch = Objects.requireNonNull(nameOrCodeMatch, "nameOrCodeMatch");
  }

  /**
   * Returns how the entry's type name or code must match.
   *
   * @return the name or code match
   */
  public ValueMatch getNameOrCodeMatch() {
    return nameOrCodeMatch;
  }

  /**
   * Returns the query matching one entry of the relationship's list whose type name or code
   * matches. Only meaningful inside the nested query on that list.
   *
   * @param relationship the node searching the list
   * @param operations the OpenSearch operations, for building the field matches
   * @return the query builder
   */
  QueryBuilder getEntriesOfType(
      final HasRelationship relationship, final OpenSearchOperations operations) {
    final NameOrCodeMatch typeNameOrCodeMatch =
        new NameOrCodeMatch(
            relationship.getTerminologies(),
            relationship.getEntryFieldName(TYPE_NAME_FIELD),
            relationship.getEntryFieldName(TYPE_CODE_FIELD),
            nameOrCodeMatch);
    return typeNameOrCodeMatch.fetchOpenSearchQuery(operations);
  }
}
