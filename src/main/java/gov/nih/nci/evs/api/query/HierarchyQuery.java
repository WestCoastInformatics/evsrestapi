package gov.nih.nci.evs.api.query;

import static gov.nih.nci.evs.api.query.RawBoolQueries.allOf;
import static gov.nih.nci.evs.api.query.RawBoolQueries.anyOf;
import static gov.nih.nci.evs.api.query.RawBoolQueries.matchNothing;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;

/**
 * Base type for nodes that match concepts related through the hierarchy to the concepts an inner
 * query matches (the anchors): {@link DescendantOf} below them, {@link AncestorOf} above them. The
 * inner query can be any {@link EvsQuery}, including another hierarchy node, so these nest.
 *
 * <p>Building the query runs the inner query first through {@link #declareDependencies}. Codes are
 * only unique within one terminology version, and each version has its own index, so each
 * terminology's anchors only match relatives in that terminology's index. Subclasses decide how to
 * find the relatives in {@link #fetchRelativesQuery}.
 *
 * <p>An optional {@code maxLevel} limits how many levels away relatives may be, by shortest
 * distance: 1 means direct children (or parents). With {@code includeSelf}, the anchors themselves
 * also match, at distance 0.
 */
public abstract class HierarchyQuery extends EvsQuery {

  /** Field holding a document's identifier, which is the concept code. */
  static final String ID_FIELD = "_id";

  /** Metadata field naming the index a document is in; one index per terminology version. */
  static final String INDEX_FIELD = "_index";

  /** The query matching the anchor concepts. */
  private final EvsQuery anchorQuery;

  /** The maximum distance from the anchors, or null for any distance. */
  private final Integer maxLevel;

  /** Whether the anchors themselves match. */
  private final boolean includeSelf;

  /**
   * Instantiates a {@link HierarchyQuery}.
   *
   * @param terminologies the terminologies being searched
   * @param anchorQuery the query matching the anchor concepts
   * @param maxLevel the maximum distance from the anchors, at least 1, or null for any distance
   * @param includeSelf whether the anchors themselves match
   */
  protected HierarchyQuery(
      final List<Terminology> terminologies,
      final EvsQuery anchorQuery,
      final Integer maxLevel,
      final boolean includeSelf) {
    super(terminologies);
    this.anchorQuery = Objects.requireNonNull(anchorQuery, "anchorQuery");
    if (maxLevel != null && maxLevel < 1) {
      throw new IllegalArgumentException("maxLevel must be at least 1: " + maxLevel);
    }
    this.maxLevel = maxLevel;
    this.includeSelf = includeSelf;
  }

  /**
   * Returns the query matching the anchor concepts.
   *
   * @return the anchor query
   */
  public EvsQuery getAnchorQuery() {
    return anchorQuery;
  }

  /**
   * Returns the maximum distance from the anchors.
   *
   * @return the max level, or null for any distance
   */
  public Integer getMaxLevel() {
    return maxLevel;
  }

  /**
   * Indicates whether the anchors themselves match.
   *
   * @return true if the anchors match
   */
  public boolean isIncludeSelf() {
    return includeSelf;
  }

  /* see superclass */
  @Override
  public final QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final ConceptKeys anchors = declareDependencies(operations, anchorQuery).get(0);

    final List<QueryBuilder> perTerminology = new ArrayList<>();
    for (final Terminology terminology : terminologies) {
      final Set<String> anchorCodes =
          anchors
              .getCodesByTerminologyVersion()
              .getOrDefault(
                  new ConceptKeys.TerminologyVersion(
                      terminology.getTerminology(), terminology.getVersion()),
                  Set.of());
      if (anchorCodes.isEmpty()) {
        continue;
      }
      final QueryBuilder relatives = fetchRelativesQuery(operations, terminology, anchorCodes);
      perTerminology.add(
          allOf(
              inIndexOf(operations, terminology),
              includeSelf
                  ? anyOf(List.of(relatives, codeIn(operations, anchorCodes)))
                  : relatives));
    }
    return perTerminology.isEmpty() ? matchNothing() : anyOf(perTerminology);
  }

  /**
   * Returns the condition matching the anchors' relatives in one terminology, not the anchors
   * themselves (unless one anchor is a relative of another).
   *
   * @param operations the OpenSearch operations, for any lookups the subclass needs
   * @param terminology the terminology
   * @param anchorCodes the anchor codes found in that terminology
   * @return the condition
   */
  protected abstract QueryBuilder fetchRelativesQuery(
      OpenSearchOperations operations, Terminology terminology, Set<String> anchorCodes);

  /**
   * Returns the condition matching concepts whose code is in the set, or nothing for an empty set.
   * A concept's document id is its code; the {@code code} field itself is analyzed text, so it
   * can't be matched exactly.
   *
   * @param operations the OpenSearch operations
   * @param codes the codes
   * @return the condition
   */
  protected QueryBuilder codeIn(final OpenSearchOperations operations, final Set<String> codes) {
    return codes.isEmpty()
        ? matchNothing()
        : new MatchAnyOf(terminologies, ID_FIELD, codes).fetchOpenSearchQuery(operations);
  }

  /**
   * Returns the condition matching documents in one terminology's index.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @return the condition
   */
  private QueryBuilder inIndexOf(
      final OpenSearchOperations operations, final Terminology terminology) {
    return new Match(terminologies, INDEX_FIELD, terminology.getIndexName())
        .fetchOpenSearchQuery(operations);
  }

  /**
   * Returns a query matching one concept by code, for the code convenience constructors.
   *
   * @param terminologies the terminologies being searched
   * @param code the code
   * @return the query
   */
  static EvsQuery codeQuery(final List<Terminology> terminologies, final String code) {
    return new Match(terminologies, ID_FIELD, Objects.requireNonNull(code, "code"));
  }
}
