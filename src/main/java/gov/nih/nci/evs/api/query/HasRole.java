package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.ConceptMinimal;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import java.util.Set;
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
 * matches the concepts it found (see {@link RoleTarget}). A role's target code refers to a concept
 * in the same terminology and version as the role's concept, and two versions share codes. So a
 * target found in one terminology version only matches roles on concepts from that version.
 *
 * <p>Needs concept indexes mapped with nested roles. On an index loaded before that mapping, the
 * search fails instead of silently returning nothing.
 */
public class HasRole extends EvsQuery implements SupportsIgnoringCase {

  /** Path of the nested role entries in a concept document. */
  private static final String ROLES_PATH = "roles";

  /** Field holding the code of the concept a role points to. */
  private static final String TARGET_CODE_FIELD = "roles.relatedCode";

  /** Exact terminology of the concept holding the roles. */
  private static final String TERMINOLOGY_FIELD = "terminology." + ConceptMinimal.KEYWORD_SUFFIX;

  /** Exact version of the concept holding the roles. */
  private static final String VERSION_FIELD = "version." + ConceptMinimal.KEYWORD_SUFFIX;

  /** A concept matches when its roles match the targets of any one terminology version. */
  private static final int MINIMUM_MATCHING_TERMINOLOGY_VERSIONS = 1;

  /**
   * Most target codes in one terminology version: OpenSearch's default index.max_terms_count, the
   * most codes one {@code terms} query accepts.
   */
  static final int MAX_TARGET_CODES = 65_536;

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
    return getRoleQuery(roleType.getQueryBuilder(), operations);
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
    return getRoleQuery(roleType.getCaseInsensitiveQueryBuilder(), operations);
  }

  /**
   * Builds the query on this node's roles. Without a target, any role entry of the type matches.
   * With one, the target query runs first, and a role entry must point to one of the concepts it
   * found in the same terminology version.
   *
   * @param hasRoleNameOrCodeMatchingRoleTypeQuery the query matching a role entry whose name or
   *     code is the role type
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  private QueryBuilder getRoleQuery(
      final QueryBuilder hasRoleNameOrCodeMatchingRoleTypeQuery,
      final OpenSearchOperations operations) {
    final boolean hasRoleTarget = getHasRoleTarget();
    if (!hasRoleTarget) {
      return getConceptsWithAnyRoleMatching(hasRoleNameOrCodeMatchingRoleTypeQuery);
    }
    final ConceptKeys targetKeys = declareDependencies(operations, roleTarget.getQuery()).get(0);
    final boolean hasFoundTargetConcepts = !targetKeys.getCodesByTerminologyVersion().isEmpty();
    if (!hasFoundTargetConcepts) {
      // A bool query with no clauses would match everything, so match nothing explicitly
      return QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery());
    }
    return getTargetInOwnVersionQuery(
        hasRoleNameOrCodeMatchingRoleTypeQuery, targetKeys, operations);
  }

  /**
   * Builds a query matching concepts whose role points to a target concept from the concept's own
   * terminology version. Versions share codes, so a target found in one version must not match
   * roles on concepts from another. There is one clause per version the targets came from, and a
   * concept matches when the clause for its version does.
   *
   * @param hasRoleNameOrCodeMatchingRoleTypeQuery the query matching a role entry whose name or
   *     code is the role type
   * @param targetKeys the target concepts, from any of the searched terminology versions
   * @param operations the OpenSearch operations, for building the atomic queries
   * @return the query builder
   */
  private QueryBuilder getTargetInOwnVersionQuery(
      final QueryBuilder hasRoleNameOrCodeMatchingRoleTypeQuery,
      final ConceptKeys targetKeys,
      final OpenSearchOperations operations) {
    final BoolQueryBuilder targetInOwnVersionQuery =
        QueryBuilders.boolQuery().minimumShouldMatch(MINIMUM_MATCHING_TERMINOLOGY_VERSIONS);
    targetKeys
        .getCodesByTerminologyVersion()
        .forEach(
            (terminologyVersion, targetCodes) ->
                targetInOwnVersionQuery.should(
                    getTerminologyVersionQuery(
                        hasRoleNameOrCodeMatchingRoleTypeQuery,
                        terminologyVersion,
                        targetCodes,
                        operations)));
    return targetInOwnVersionQuery;
  }

  /**
   * Builds a query matching concepts from one terminology version whose role points to one of that
   * version's target codes. The terminology and version are checked on the concept, outside the
   * nested query, because a nested query only sees the role entry's own fields.
   *
   * @param hasRoleNameOrCodeMatchingRoleTypeQuery the query matching a role entry whose name or
   *     code is the role type
   * @param terminologyVersion the terminology version
   * @param targetCodes the target codes in that terminology version
   * @param operations the OpenSearch operations, for building the atomic queries
   * @return the query builder
   * @throws IllegalStateException if there are more target codes than one {@code terms} query
   *     accepts
   */
  private QueryBuilder getTerminologyVersionQuery(
      final QueryBuilder hasRoleNameOrCodeMatchingRoleTypeQuery,
      final ConceptKeys.TerminologyVersion terminologyVersion,
      final Set<String> targetCodes,
      final OpenSearchOperations operations) {
    checkTargetCodeCount(targetCodes.size());
    final QueryBuilder roleEntryConditions =
        QueryBuilders.boolQuery()
            .must(hasRoleNameOrCodeMatchingRoleTypeQuery)
            .must(QueryBuilders.termsQuery(TARGET_CODE_FIELD, targetCodes));
    final Match terminologyMatch =
        new Match(terminologies, TERMINOLOGY_FIELD, terminologyVersion.terminology());
    final Match versionMatch =
        new Match(terminologies, VERSION_FIELD, terminologyVersion.version());
    return QueryBuilders.boolQuery()
        .filter(terminologyMatch.fetchOpenSearchQuery(operations))
        .filter(versionMatch.fetchOpenSearchQuery(operations))
        .filter(getConceptsWithAnyRoleMatching(roleEntryConditions));
  }

  /**
   * Builds a query matching concepts that have at least one role entry satisfying all the given
   * conditions. A role entry is one item in the concept's {@code roles} list, pairing a type with a
   * target.
   *
   * <p>The conditions must hold on the same entry, which is why this is a {@code nested} query.
   * Without it, OpenSearch flattens every entry's fields together, and "Lung Carcinoma" (primary
   * site Lung, associated site Skin) would match "primary site: Skin" by taking the type from one
   * entry and the target from the other.
   *
   * @param roleEntryConditions the conditions one role entry must meet
   * @return the query builder
   */
  private static QueryBuilder getConceptsWithAnyRoleMatching(
      final QueryBuilder roleEntryConditions) {
    return QueryBuilders.nestedQuery(ROLES_PATH, roleEntryConditions, ScoreMode.None);
  }

  /**
   * Fails if a terminology version has more target codes than one {@code terms} query accepts,
   * rather than cutting the codes short and silently giving wrong results.
   *
   * @param targetCodeCount the number of target codes in one terminology version
   * @throws IllegalStateException if there are too many
   */
  private static void checkTargetCodeCount(final int targetCodeCount) {
    final boolean hasTooManyTargetCodes = targetCodeCount > MAX_TARGET_CODES;
    if (hasTooManyTargetCodes) {
      throw new IllegalStateException(
          "A role target matched more than "
              + MAX_TARGET_CODES
              + " concepts in one terminology version, more than one OpenSearch terms query"
              + " accepts. Narrow the role target.");
    }
  }
}
