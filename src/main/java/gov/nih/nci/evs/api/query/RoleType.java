package gov.nih.nci.evs.api.query;

import java.util.Objects;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * The type of role a {@link HasRole} looks for, given as the role's exact name (e.g.
 * "Disease_Has_Primary_Anatomic_Site") or its code (e.g. "R101"). Matching is case sensitive. To
 * ignore case, wrap the {@link HasRole} in {@link CaseInsensitive}.
 *
 * <p>Only literal names and codes are accepted, not atomic queries like {@link Fuzzy} or {@link
 * Contains}:
 *
 * <ul>
 *   <li>Role types are a small, closed vocabulary (a few dozen in NCIt), all listed by the role
 *       metadata endpoint. Clients can look the exact name or code up instead of guessing it.
 *   <li>A mistyped role type should match nothing, not quietly match a neighbor. Fuzzy and contains
 *       matching would treat "Disease_Has_Primary_Anatomic_Site" and
 *       "Disease_Has_Associated_Anatomic_Site" as close, returning roles the caller didn't ask for.
 *   <li>The legitimate reason for looser matching is grouping, e.g. "any anatomic site role". That
 *       is clearer as an explicit {@code Or} of role types, or as a lookup the client (Explore,
 *       MCP) does against the role metadata first.
 * </ul>
 *
 * <p>Looser matching belongs on the role's target instead (see {@link RoleTarget}), because target
 * concepts have names that people type.
 */
public final class RoleType {

  /** Field holding the role's name. */
  private static final String NAME_FIELD = "roles.type";

  /** Field holding the role's code. */
  private static final String CODE_FIELD = "roles.code";

  /** A role matches when either its name or its code matches. */
  private static final int MINIMUM_MATCHING_FIELDS = 1;

  /** The role's exact name or code. */
  private final String nameOrCode;

  /**
   * Instantiates a {@link RoleType}.
   *
   * @param nameOrCode the role's exact name or code
   */
  public RoleType(final String nameOrCode) {
    this.nameOrCode = Objects.requireNonNull(nameOrCode, "nameOrCode");
  }

  /**
   * Returns the role's exact name or code.
   *
   * @return the name or code
   */
  public String getNameOrCode() {
    return nameOrCode;
  }

  /**
   * Returns the query matching one role entry's name or code. Only meaningful inside the nested
   * roles query built by {@link HasRole}.
   *
   * @return the query builder
   */
  QueryBuilder getQueryBuilder() {
    return getNameOrCodeQuery(
        QueryBuilders.termQuery(NAME_FIELD, nameOrCode),
        QueryBuilders.termQuery(CODE_FIELD, nameOrCode));
  }

  /**
   * Returns {@link #getQueryBuilder()} with case ignored. Used by {@link HasRole} when it is
   * wrapped in {@link CaseInsensitive}. A role type can't be wrapped on its own.
   *
   * @return the case-insensitive query builder
   */
  QueryBuilder getCaseInsensitiveQueryBuilder() {
    return getNameOrCodeQuery(
        QueryBuilders.termQuery(NAME_FIELD, nameOrCode).caseInsensitive(true),
        QueryBuilders.termQuery(CODE_FIELD, nameOrCode).caseInsensitive(true));
  }

  /**
   * Combines the name and code queries so either one matching is enough.
   *
   * @param nameQuery the query on the role's name
   * @param codeQuery the query on the role's code
   * @return the query builder
   */
  private static QueryBuilder getNameOrCodeQuery(
      final QueryBuilder nameQuery, final QueryBuilder codeQuery) {
    return QueryBuilders.boolQuery()
        .should(nameQuery)
        .should(codeQuery)
        .minimumShouldMatch(MINIMUM_MATCHING_FIELDS);
  }
}
