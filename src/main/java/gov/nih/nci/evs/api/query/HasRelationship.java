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
 * Matches concepts with a relationship of the given type in one of their relationship lists,
 * optionally pointing to the given target, e.g. concepts whose primary anatomic site role points to
 * Skin or anything below Skin. Roles and associations are both lists of entries pairing a type with
 * a related concept, so one node searches either: only the list differs. Each subclass says which
 * list it searches ({@link #getRelationshipListFieldName}), e.g. {@link HasRole} searches {@code
 * roles}.
 *
 * <p>The type and target must match on the same entry. A concept with "primary site: Lung" and
 * "associated site: Skin" does not match "primary site: Skin".
 *
 * <p>How the type is matched is up to the {@link RelationshipType}'s {@link ValueMatch}. Target
 * codes are always matched exactly.
 *
 * <p>A target is a query over other concepts. Building this query runs the target query first and
 * matches the concepts it found (see {@link RelationshipTarget}). A relationship's target code
 * refers to a concept in the same terminology and version as the relationship's concept, and two
 * versions share codes. So a target found in one terminology version only matches relationships on
 * concepts from that version.
 *
 * <p>Needs concept indexes mapped with the list as nested. On an index loaded before that mapping,
 * the search fails instead of silently returning nothing.
 */
public abstract class HasRelationship extends EvsQuery {

  /** Exact terminology of the concept holding the relationships. */
  private static final String TERMINOLOGY_FIELD = "terminology." + ConceptMinimal.KEYWORD_SUFFIX;

  /** Exact version of the concept holding the relationships. */
  private static final String VERSION_FIELD = "version." + ConceptMinimal.KEYWORD_SUFFIX;

  /** A concept matches when its relationships match the targets of any one terminology version. */
  private static final int MINIMUM_MATCHING_TERMINOLOGY_VERSIONS = 1;

  /**
   * Most target codes in one terminology version: OpenSearch's default index.max_terms_count, the
   * most codes one {@code terms} query accepts.
   */
  static final int MAX_TARGET_CODES = 65_536;

  /**
   * The relationship's type: the kind of relationship the entry expresses, e.g.
   * "Disease_Has_Primary_Anatomic_Site" (code "R101"). A concept has many entries, each pairing a
   * type with a target concept, and the type says how the concept relates to that target.
   *
   * <p>It's its own object, not a plain match, because a type can be given by name or by code, so
   * the query checks both of the entry's fields. {@link RelationshipType} keeps that rule in one
   * place.
   */
  private final RelationshipType relationshipType;

  /** The concepts the relationship must point to, or null for any target. */
  private final RelationshipTarget relationshipTarget;

  /**
   * Instantiates a {@link HasRelationship} that matches a relationship of the given type, pointing
   * to any concept.
   *
   * @param terminologies the terminologies being searched
   * @param relationshipType the relationship's type
   */
  protected HasRelationship(
      final List<Terminology> terminologies, final RelationshipType relationshipType) {
    super(terminologies);
    this.relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
    this.relationshipTarget = null;
  }

  /**
   * Instantiates a {@link HasRelationship} that matches a relationship of the given type pointing
   * to the target.
   *
   * @param terminologies the terminologies being searched
   * @param relationshipType the relationship's type
   * @param relationshipTarget the concepts the relationship must point to
   */
  protected HasRelationship(
      final List<Terminology> terminologies,
      final RelationshipType relationshipType,
      final RelationshipTarget relationshipTarget) {
    super(terminologies);
    this.relationshipType = Objects.requireNonNull(relationshipType, "relationshipType");
    this.relationshipTarget = Objects.requireNonNull(relationshipTarget, "relationshipTarget");
  }

  /**
   * Returns the name of the concept document's list of relationships this node searches, e.g.
   * {@code roles}. OpenSearch calls it the nested path. Each entry's fields are named with it: the
   * list's name, a dot, and the field, e.g. {@code roles.relatedCode}.
   *
   * <p>The list must be mapped as nested in {@code Concept}, or the search fails.
   *
   * @return the list's field name
   */
  protected abstract String getRelationshipListFieldName();

  /**
   * Returns the field holding an entry's type name, e.g. "Disease_Has_Primary_Anatomic_Site".
   *
   * @return the field name
   */
  private String getTypeNameField() {
    return getRelationshipListFieldName() + ".type";
  }

  /**
   * Returns the field holding an entry's type code, e.g. "R101".
   *
   * @return the field name
   */
  private String getTypeCodeField() {
    return getRelationshipListFieldName() + ".code";
  }

  /**
   * Returns the field holding the code of the concept an entry points to.
   *
   * @return the field name
   */
  private String getRelatedCodeField() {
    return getRelationshipListFieldName() + ".relatedCode";
  }

  /**
   * Returns the relationship's type.
   *
   * @return the relationship type
   */
  public RelationshipType getRelationshipType() {
    return relationshipType;
  }

  /**
   * Indicates whether the relationship must point to a specific target.
   *
   * @return whether there is a target
   */
  public boolean getHasRelationshipTarget() {
    return relationshipTarget != null;
  }

  /**
   * Returns the concepts the relationship must point to.
   *
   * @return the relationship target, or null for any target
   */
  public RelationshipTarget getRelationshipTarget() {
    return relationshipTarget;
  }

  /**
   * Returns the OpenSearch query for this node: a concept matches when one of the entries in its
   * relationship list has this type and, if there is one, points to one of the target concepts. If
   * there's a target, its query runs first.
   *
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return getRelationshipQuery(
        relationshipType.getQueryBuilder(getTypeNameField(), getTypeCodeField()), operations);
  }

  /**
   * Builds the query on this node's relationship list. Without a target, any entry of the type
   * matches. With one, the target query runs first, and an entry must point to one of the concepts
   * it found in the same terminology version.
   *
   * @param hasRelationshipNameOrCodeMatchingRelationshipTypeQuery the query matching an entry whose
   *     type name or code is the relationship type
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  private QueryBuilder getRelationshipQuery(
      final QueryBuilder hasRelationshipNameOrCodeMatchingRelationshipTypeQuery,
      final OpenSearchOperations operations) {
    final boolean hasRelationshipTarget = getHasRelationshipTarget();
    if (!hasRelationshipTarget) {
      return getConceptsWithAnyRelationshipMatching(
          hasRelationshipNameOrCodeMatchingRelationshipTypeQuery);
    }

    final ConceptKeys targetKeys =
        declareDependencies(operations, relationshipTarget.getQuery()).get(0);

    final boolean hasFoundTargetConcepts = !targetKeys.getCodesByTerminologyVersion().isEmpty();
    if (!hasFoundTargetConcepts) {
      // A bool query with no clauses would match everything, so match nothing explicitly
      return QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery());
    }

    return getTargetInOwnVersionQuery(
        hasRelationshipNameOrCodeMatchingRelationshipTypeQuery, targetKeys, operations);
  }

  /**
   * Builds a query matching concepts whose relationship points to a target concept from the
   * concept's own terminology version. Versions share codes, so a target found in one version must
   * not match relationships on concepts from another. There is one clause per version the targets
   * came from, and a concept matches when the clause for its version does.
   *
   * @param hasRelationshipNameOrCodeMatchingRelationshipTypeQuery the query matching an entry whose
   *     type name or code is the relationship type
   * @param targetKeys the target concepts, from any of the searched terminology versions
   * @param operations the OpenSearch operations, for building the field matches
   * @return the query builder
   */
  private QueryBuilder getTargetInOwnVersionQuery(
      final QueryBuilder hasRelationshipNameOrCodeMatchingRelationshipTypeQuery,
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
                        hasRelationshipNameOrCodeMatchingRelationshipTypeQuery,
                        terminologyVersion,
                        targetCodes,
                        operations)));

    return targetInOwnVersionQuery;
  }

  /**
   * Builds a query matching concepts from one terminology version whose relationship points to one
   * of that version's target codes. The terminology and version are checked on the concept, outside
   * the nested query, because a nested query only sees the entry's own fields.
   *
   * @param hasRelationshipNameOrCodeMatchingRelationshipTypeQuery the query matching an entry whose
   *     type name or code is the relationship type
   * @param terminologyVersion the terminology version
   * @param targetCodes the target codes in that terminology version
   * @param operations the OpenSearch operations, for building the field matches
   * @return the query builder
   * @throws IllegalStateException if there are more target codes than one {@code terms} query
   *     accepts
   */
  private QueryBuilder getTerminologyVersionQuery(
      final QueryBuilder hasRelationshipNameOrCodeMatchingRelationshipTypeQuery,
      final ConceptKeys.TerminologyVersion terminologyVersion,
      final Set<String> targetCodes,
      final OpenSearchOperations operations) {
    checkTargetCodeCount(targetCodes.size());

    final QueryBuilder relationshipEntryConditions =
        QueryBuilders.boolQuery()
            .must(hasRelationshipNameOrCodeMatchingRelationshipTypeQuery)
            .must(QueryBuilders.termsQuery(getRelatedCodeField(), targetCodes));

    final FieldMatch terminologyMatch =
        new FieldMatch(
            terminologies, TERMINOLOGY_FIELD, new Exact(terminologyVersion.terminology()));
    final FieldMatch versionMatch =
        new FieldMatch(terminologies, VERSION_FIELD, new Exact(terminologyVersion.version()));

    return QueryBuilders.boolQuery()
        .filter(terminologyMatch.fetchOpenSearchQuery(operations))
        .filter(versionMatch.fetchOpenSearchQuery(operations))
        .filter(getConceptsWithAnyRelationshipMatching(relationshipEntryConditions));
  }

  /**
   * Builds a query matching concepts that have at least one entry in this node's relationship list
   * satisfying all the given conditions. An entry is one item in the list, pairing a type with a
   * target.
   *
   * <p>The conditions must hold on the same entry, which is why this is a {@code nested} query.
   * Without it, OpenSearch flattens every entry's fields together, and "Lung Carcinoma" (primary
   * site Lung, associated site Skin) would match "primary site: Skin" by taking the type from one
   * entry and the target from the other.
   *
   * @param relationshipEntryConditions the conditions one entry must meet
   * @return the query builder
   */
  private QueryBuilder getConceptsWithAnyRelationshipMatching(
      final QueryBuilder relationshipEntryConditions) {
    return QueryBuilders.nestedQuery(
        getRelationshipListFieldName(), relationshipEntryConditions, ScoreMode.None);
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
          "A relationship target matched more than "
              + MAX_TARGET_CODES
              + " concepts in one terminology version, more than one OpenSearch terms query"
              + " accepts. Narrow the relationship target.");
    }
  }
}
