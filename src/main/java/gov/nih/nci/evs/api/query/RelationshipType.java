package gov.nih.nci.evs.api.query;

import java.util.Objects;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * The type of relationship a {@link HasRelationship} looks for, e.g.
 * "Disease_Has_Primary_Anatomic_Site" (code "R101"). An entry matches when either its type name or
 * its type code matches the {@link ValueMatch}, so the caller picks how the type is matched without
 * naming the entry's fields: {@code new RelationshipType(new StartsWith("Disease_Has_Primary"))}.
 * Wrap the match in {@link CaseInsensitive} to ignore case.
 */
public final class RelationshipType {

  /** An entry matches when either its type name or its type code matches. */
  private static final int MINIMUM_MATCHING_FIELDS = 1;

  /** How the entry's type name or code must match. */
  private final ValueMatch match;

  /**
   * Instantiates a {@link RelationshipType} matching the type's exact name or code.
   *
   * @param nameOrCode the type's exact name or code
   */
  public RelationshipType(final String nameOrCode) {
    this(new Exact(nameOrCode));
  }

  /**
   * Instantiates a {@link RelationshipType}.
   *
   * @param match how the entry's type name or code must match
   */
  public RelationshipType(final ValueMatch match) {
    this.match = Objects.requireNonNull(match, "match");
  }

  /**
   * Returns how the entry's type name or code must match.
   *
   * @return the match
   */
  public ValueMatch getMatch() {
    return match;
  }

  /**
   * Returns the query matching one relationship entry whose type name or code matches. Only
   * meaningful inside the nested query on the entry's list built by {@link HasRelationship}, which
   * says which fields hold the entry's type.
   *
   * @param typeNameField the field holding the entry's type name, e.g. {@code roles.type}
   * @param typeCodeField the field holding the entry's type code, e.g. {@code roles.code}
   * @return the query builder
   */
  QueryBuilder getQueryBuilder(final String typeNameField, final String typeCodeField) {
    return QueryBuilders.boolQuery()
        .should(match.getQueryBuilder(typeNameField))
        .should(match.getQueryBuilder(typeCodeField))
        .minimumShouldMatch(MINIMUM_MATCHING_FIELDS);
  }
}
