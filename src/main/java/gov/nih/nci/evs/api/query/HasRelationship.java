package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.ConceptMinimal;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;

/**
 * Matches concepts with a relationship of the given type in one of their relationship lists,
 * optionally pointing to the given target, e.g. concepts whose primary anatomic site role points to
 * Skin or anything below Skin. Roles and associations are both lists of entries pairing a type with
 * a related concept, so one node searches either: only the list differs. Each subclass says which
 * list it searches ({@link #getRelationshipListFieldName}): {@link HasRole} searches {@code roles}
 * and {@link HasAssociation} searches {@code associations}.
 *
 * <p>The type and target must match on the same entry. A concept with "primary site: Lung" and
 * "associated site: Skin" does not match "primary site: Skin".
 *
 * <p>This node owns the list: which one it is, the nested query on it, and the terminology version
 * filter on the concept. The {@link RelationshipType} and {@link RelationshipTarget} own which of
 * an entry's fields they compare, and how.
 *
 * <p>Needs concept indexes mapped with the list as nested. On an index loaded before that mapping,
 * the search fails instead of silently returning nothing.
 */
public abstract class HasRelationship extends EvsQuery {

  /** Exact terminology of the concept holding the relationships. */
  private static final String TERMINOLOGY_FIELD = "terminology." + ConceptMinimal.KEYWORD_SUFFIX;

  /** Exact version of the concept holding the relationships. */
  private static final String VERSION_FIELD = "version." + ConceptMinimal.KEYWORD_SUFFIX;

  /** Joins the list's name and an entry field into the entry field's full name. */
  private static final String ENTRY_FIELD_SEPARATOR = ".";

  /** The entry field holding the target's code, which the facet counts. */
  private static final String TARGET_CODE_FIELD = "relatedCode";

  /**
   * The relationship's type: the kind of relationship the entry expresses, e.g.
   * "Disease_Has_Primary_Anatomic_Site" (code "R101"). A concept has many entries, each pairing a
   * type with a target concept, and the type says how the concept relates to that target.
   */
  private final RelationshipType relationshipType;

  /** What the relationship must point to, or null for any target. */
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
   * @param relationshipTarget what the relationship must point to
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
   * {@code roles}. OpenSearch calls it the nested path.
   *
   * <p>The list must be mapped as nested in {@code Concept}, or the search fails.
   *
   * @return the list's field name
   */
  protected abstract String getRelationshipListFieldName();

  /**
   * Returns the full name of a field on this node's entries: the list's name, a dot, and the field,
   * e.g. {@code roles.relatedCode}.
   *
   * @param entryField the field on one entry, e.g. {@code relatedCode}
   * @return the full field name
   */
  String getEntryFieldName(final String entryField) {
    return getRelationshipListFieldName() + ENTRY_FIELD_SEPARATOR + entryField;
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
   * Returns what the relationship must point to.
   *
   * @return the relationship target, or null for any target
   */
  public RelationshipTarget getRelationshipTarget() {
    return relationshipTarget;
  }

  /**
   * Returns the OpenSearch query for this node: a concept matches when one of the entries in its
   * relationship list has this type and, if there is one, points to the target. If the target is a
   * query, it runs first.
   *
   * @param operations the OpenSearch operations, for running a target query
   * @return the query builder
   */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final QueryBuilder entriesOfType = relationshipType.getEntriesOfType(this, operations);

    final boolean hasRelationshipTarget = getHasRelationshipTarget();
    if (!hasRelationshipTarget) {
      return getConceptsWithAnyEntryMatching(entriesOfType);
    }

    return relationshipTarget.fetchConceptsPointingToTarget(this, entriesOfType, operations);
  }

  /**
   * Returns this node's facet: for its relationship type, how many concepts point to each target,
   * e.g. for a primary site role, each site. Named after the list and the type, e.g. "roles:R101".
   *
   * @param operations the OpenSearch operations
   * @return the facet aggregation
   */
  @Override
  public List<AbstractAggregationBuilder<?>> fetchFacetBuilders(
      final OpenSearchOperations operations) {
    final String facetName =
        FacetAggregations.getFacetName(
            getRelationshipListFieldName(),
            FacetAggregations.getMatchKey(relationshipType.getNameOrCodeMatch()));
    final QueryBuilder entriesOfType = relationshipType.getEntriesOfType(this, operations);
    return List.of(
        FacetAggregations.getNestedTermsFacet(
            facetName,
            getRelationshipListFieldName(),
            entriesOfType,
            getEntryFieldName(TARGET_CODE_FIELD)));
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
   * @param entryConditions the conditions one entry must meet
   * @return the query builder
   */
  QueryBuilder getConceptsWithAnyEntryMatching(final QueryBuilder entryConditions) {
    return QueryBuilders.nestedQuery(
        getRelationshipListFieldName(), entryConditions, ScoreMode.None);
  }

  /**
   * Builds a query matching concepts from one terminology version that have at least one entry
   * satisfying all the given conditions. The terminology and version are checked on the concept,
   * outside the nested query, because a nested query only sees the entry's own fields.
   *
   * @param terminologyVersion the terminology version the concept must be from
   * @param entryConditions the conditions one entry must meet
   * @param operations the OpenSearch operations, for building the field matches
   * @return the query builder
   */
  QueryBuilder getConceptsInVersionWithAnyEntryMatching(
      final ConceptKeys.TerminologyVersion terminologyVersion,
      final QueryBuilder entryConditions,
      final OpenSearchOperations operations) {
    final FieldMatch terminologyMatch =
        new FieldMatch(
            terminologies, TERMINOLOGY_FIELD, new Exact(terminologyVersion.terminology()));
    final FieldMatch versionMatch =
        new FieldMatch(terminologies, VERSION_FIELD, new Exact(terminologyVersion.version()));

    return QueryBuilders.boolQuery()
        .filter(terminologyMatch.fetchOpenSearchQuery(operations))
        .filter(versionMatch.fetchOpenSearchQuery(operations))
        .filter(getConceptsWithAnyEntryMatching(entryConditions));
  }
}
