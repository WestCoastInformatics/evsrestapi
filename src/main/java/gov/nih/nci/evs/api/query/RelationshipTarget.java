package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.Set;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * What a relationship must point to, for {@link HasRelationship}. Given one of two ways:
 *
 * <ul>
 *   <li>A {@link ValueMatch}, compared directly against the entry's target name or target code,
 *       either one matching: {@code new RelationshipTarget(new Fuzzy("skinnn"))}. No other search
 *       runs. The target name is analyzed text, so the match compares its individual lowercased
 *       words, not the whole name.
 *   <li>A query over concepts, e.g. "Skin or anything below Skin". A relationship entry only stores
 *       its target's code, so the query describes a different concept from the one being matched
 *       and can't run inside the relationship query. It runs first (see {@link
 *       EvsQuery#declareDependencies}), and the entry's target code must be {@link OneOf} the codes
 *       it found. A target query can hold relationship targets of its own, which run first in the
 *       same way.
 * </ul>
 *
 * <p>A target code refers to a concept in the same terminology and version as the relationship's
 * concept, and two versions share codes. So a target the query found in one terminology version
 * only matches relationships on concepts from that version.
 */
public final class RelationshipTarget {

  /** The entry field holding the name of the concept the entry points to. Analyzed text. */
  private static final String TARGET_NAME_FIELD = "relatedName";

  /** The entry field holding the code of the concept the entry points to. */
  private static final String TARGET_CODE_FIELD = "relatedCode";

  /** A concept matches when its relationships match the targets of any one terminology version. */
  private static final int MINIMUM_MATCHING_TERMINOLOGY_VERSIONS = 1;

  /**
   * Most target codes in one terminology version: OpenSearch's default index.max_terms_count, the
   * most codes one {@code terms} query accepts.
   */
  static final int MAX_TARGET_CODES = 65_536;

  /** Builds the query matching concepts whose relationship points to this target. */
  private final TargetQueryBuilder targetQueryBuilder;

  /**
   * Instantiates a {@link RelationshipTarget} that runs the query first and matches the codes it
   * found.
   *
   * @param query the query selecting the target concepts
   */
  public RelationshipTarget(final EvsQuery query) {
    Objects.requireNonNull(query, "query");
    this.targetQueryBuilder =
        (relationship, entriesOfType, operations) ->
            fetchConceptsPointingToAnyConceptMatching(
                relationship, entriesOfType, query, operations);
  }

  /**
   * Instantiates a {@link RelationshipTarget} compared directly against the entry's target name or
   * target code.
   *
   * @param nameOrCodeMatch how the entry's target name or code must match
   */
  public RelationshipTarget(final ValueMatch nameOrCodeMatch) {
    Objects.requireNonNull(nameOrCodeMatch, "nameOrCodeMatch");
    this.targetQueryBuilder =
        (relationship, entriesOfType, operations) ->
            getConceptsPointingToNameOrCodeMatching(
                relationship, entriesOfType, nameOrCodeMatch, operations);
  }

  /**
   * Returns the query matching concepts with an entry of the type that points to this target.
   *
   * @param relationship the node searching the relationship list
   * @param entriesOfType the query matching an entry of the relationship's type
   * @param operations the OpenSearch operations, for running a target query
   * @return the query builder
   */
  QueryBuilder fetchConceptsPointingToTarget(
      final HasRelationship relationship,
      final QueryBuilder entriesOfType,
      final OpenSearchOperations operations) {
    return targetQueryBuilder.fetchConceptsPointingToTarget(
        relationship, entriesOfType, operations);
  }

  /**
   * Builds the query matching concepts with an entry of the type whose target name or code matches.
   * The match is compared against values stored on the entry itself, so no other search runs.
   *
   * @param relationship the node searching the relationship list
   * @param entriesOfType the query matching an entry of the relationship's type
   * @param nameOrCodeMatch how the entry's target name or code must match
   * @param operations the OpenSearch operations, for building the field matches
   * @return the query builder
   */
  private static QueryBuilder getConceptsPointingToNameOrCodeMatching(
      final HasRelationship relationship,
      final QueryBuilder entriesOfType,
      final ValueMatch nameOrCodeMatch,
      final OpenSearchOperations operations) {
    final NameOrCodeMatch targetNameOrCodeMatch =
        new NameOrCodeMatch(
            relationship.getTerminologies(),
            relationship.getEntryFieldName(TARGET_NAME_FIELD),
            relationship.getEntryFieldName(TARGET_CODE_FIELD),
            nameOrCodeMatch);
    final QueryBuilder entryConditions =
        QueryBuilders.boolQuery()
            .must(entriesOfType)
            .must(targetNameOrCodeMatch.fetchOpenSearchQuery(operations));

    return relationship.getConceptsWithAnyEntryMatching(entryConditions);
  }

  /**
   * Builds the query matching concepts with an entry of the type that points to a concept the
   * target query finds. The target query runs first. There is one clause per terminology version
   * the targets came from, and a concept matches when the clause for its version does.
   *
   * @param relationship the node searching the relationship list
   * @param entriesOfType the query matching an entry of the relationship's type
   * @param targetQuery the query selecting the target concepts
   * @param operations the OpenSearch operations, for running the target query
   * @return the query builder
   */
  private static QueryBuilder fetchConceptsPointingToAnyConceptMatching(
      final HasRelationship relationship,
      final QueryBuilder entriesOfType,
      final EvsQuery targetQuery,
      final OpenSearchOperations operations) {
    final ConceptKeys targetKeys = EvsQuery.declareDependencies(operations, targetQuery).get(0);

    final boolean hasFoundTargetConcepts = !targetKeys.getCodesByTerminologyVersion().isEmpty();
    if (!hasFoundTargetConcepts) {
      // A bool query with no clauses would match everything, so match nothing explicitly
      return QueryBuilders.boolQuery().mustNot(QueryBuilders.matchAllQuery());
    }

    final BoolQueryBuilder targetInOwnVersionQuery =
        QueryBuilders.boolQuery().minimumShouldMatch(MINIMUM_MATCHING_TERMINOLOGY_VERSIONS);
    targetKeys
        .getCodesByTerminologyVersion()
        .forEach(
            (terminologyVersion, targetCodes) ->
                targetInOwnVersionQuery.should(
                    getConceptsInVersionPointingToAnyCode(
                        relationship, entriesOfType, terminologyVersion, targetCodes, operations)));

    return targetInOwnVersionQuery;
  }

  /**
   * Builds the query matching concepts from one terminology version with an entry of the type whose
   * target code is one of that version's target codes.
   *
   * @param relationship the node searching the relationship list
   * @param entriesOfType the query matching an entry of the relationship's type
   * @param terminologyVersion the terminology version
   * @param targetCodes the target codes in that terminology version
   * @param operations the OpenSearch operations, for building the field matches
   * @return the query builder
   * @throws IllegalStateException if there are more target codes than one {@code terms} query
   *     accepts
   */
  private static QueryBuilder getConceptsInVersionPointingToAnyCode(
      final HasRelationship relationship,
      final QueryBuilder entriesOfType,
      final ConceptKeys.TerminologyVersion terminologyVersion,
      final Set<String> targetCodes,
      final OpenSearchOperations operations) {
    checkTargetCodeCount(targetCodes.size());

    final FieldMatch targetCodeMatch =
        new FieldMatch(
            relationship.getTerminologies(),
            relationship.getEntryFieldName(TARGET_CODE_FIELD),
            new OneOf(targetCodes));
    final QueryBuilder entryConditions =
        QueryBuilders.boolQuery()
            .must(entriesOfType)
            .must(targetCodeMatch.fetchOpenSearchQuery(operations));

    return relationship.getConceptsInVersionWithAnyEntryMatching(
        terminologyVersion, entryConditions, operations);
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

  /** Builds the query for one way of giving a target. */
  @FunctionalInterface
  private interface TargetQueryBuilder {

    /**
     * Returns the query matching concepts with an entry of the type that points to the target.
     *
     * @param relationship the node searching the relationship list
     * @param entriesOfType the query matching an entry of the relationship's type
     * @param operations the OpenSearch operations, for running a target query
     * @return the query builder
     */
    QueryBuilder fetchConceptsPointingToTarget(
        HasRelationship relationship, QueryBuilder entriesOfType, OpenSearchOperations operations);
  }
}
