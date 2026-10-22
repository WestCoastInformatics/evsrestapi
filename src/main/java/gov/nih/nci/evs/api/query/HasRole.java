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
 * Matches concepts that have a role of the given type, optionally pointing to the given target,
 * e.g. concepts whose primary anatomic site is Skin or anything below Skin.
 *
 * <p>The type and target must match on the same role. A concept with "primary site: Lung" and
 * "associated site: Skin" does not match "primary site: Skin".
 *
 * <p>Matching is case sensitive. Wrapped in {@link CaseInsensitive}, the role type ignores case.
 * Target codes are always matched exactly.
 *
 * <p>A target is a query over other concepts. Building this query runs the target query first and
 * matches the codes it found (see {@link RoleTarget}).
 *
 * <p>Needs concept indexes mapped with nested roles. On an index loaded before that mapping, the
 * search fails instead of silently returning nothing.
 */
public class HasRole extends EvsQuery implements SupportsIgnoringCase {

  /** Path of the nested role entries in a concept document. */
  private static final String ROLES_PATH = "roles";

  /**
   * The role's type: the kind of relationship the role expresses, e.g.
   * "Disease_Has_Primary_Anatomic_Site" (code "R101"). A concept has many roles, each pairing a
   * type with a target concept, and the type says how the concept relates to that target.
   *
   * <p>It's its own object, not a plain string, because matching a type isn't just one term query.
   * A type can be given by name or by code, so the query checks both fields. It can also be matched
   * ignoring case when this query is wrapped in {@link CaseInsensitive}. {@link RoleType} keeps
   * those rules in one place, and only accepts exact names and codes, not looser atomic queries
   * (its Javadoc explains why).
   */
  private final RoleType roleType;

  /** The concepts the role must point to, or null for any target. */
  private final RoleTarget roleTarget;

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type, pointing to any concept.
   *
   * @param terminologies the terminologies being searched
   * @param roleType the role's type
   */
  public HasRole(final List<Terminology> terminologies, final RoleType roleType) {
    super(terminologies);
    this.roleType = Objects.requireNonNull(roleType, "roleType");
    this.roleTarget = null;
  }

  /**
   * Instantiates a {@link HasRole} that matches a role of the given type pointing to the target.
   *
   * @param terminologies the terminologies being searched
   * @param roleType the role's type
   * @param roleTarget the concepts the role must point to
   */
  public HasRole(
      final List<Terminology> terminologies, final RoleType roleType, final RoleTarget roleTarget) {
    super(terminologies);
    this.roleType = Objects.requireNonNull(roleType, "roleType");
    this.roleTarget = Objects.requireNonNull(roleTarget, "roleTarget");
  }

  /**
   * Returns the role's type.
   *
   * @return the role type
   */
  public RoleType getRoleType() {
    return roleType;
  }

  /**
   * Indicates whether the role must point to a specific target.
   *
   * @return whether there is a target
   */
  public boolean getHasRoleTarget() {
    return roleTarget != null;
  }

  /**
   * Returns the concepts the role must point to.
   *
   * @return the role target, or null for any target
   */
  public RoleTarget getRoleTarget() {
    return roleTarget;
  }

  /**
   * Returns the OpenSearch query for this node: a concept matches when one of its role entries has
   * this role type (matched case sensitively) and, if there is one, points to one of the target
   * concepts. If there's a target, its query runs first.
   *
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return getRoleEntryQuery(roleType.getQueryBuilder(), operations);
  }

  /**
   * Returns {@link #fetchOpenSearchQuery} with the role type matched ignoring case. Used when this
   * query is wrapped in {@link CaseInsensitive}. Target codes are still matched exactly.
   *
   * @param operations the OpenSearch operations, for running the target query
   * @return the case-insensitive query builder
   */
  @Override
  public QueryBuilder fetchCaseInsensitiveOpenSearchQuery(final OpenSearchOperations operations) {
    return getRoleEntryQuery(roleType.getCaseInsensitiveQueryBuilder(), operations);
  }

  /**
   * Builds a query matching concepts that have at least one role entry satisfying both the type
   * query and, if there is one, the target. A role entry is one item in the concept's {@code roles}
   * list, pairing a type with a target.
   *
   * <p>The conditions must hold on the same entry, which is why this is a {@code nested} query.
   * Without it, OpenSearch flattens every entry's fields together, and "Lung Carcinoma" (primary
   * site Lung, associated site Skin) would match "primary site: Skin" by taking the type from one
   * entry and the target from the other.
   *
   * @param roleTypeQuery the query on the role's type
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  private QueryBuilder getRoleEntryQuery(
      final QueryBuilder roleTypeQuery, final OpenSearchOperations operations) {
    final BoolQueryBuilder roleEntryQuery = QueryBuilders.boolQuery().must(roleTypeQuery);
    final boolean hasRoleTarget = getHasRoleTarget();
    if (hasRoleTarget) {
      roleEntryQuery.must(roleTarget.fetchOpenSearchQuery(operations));
    }
    return QueryBuilders.nestedQuery(ROLES_PATH, roleEntryQuery, ScoreMode.None);
  }
}
