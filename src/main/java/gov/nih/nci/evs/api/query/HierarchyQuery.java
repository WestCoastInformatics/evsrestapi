package gov.nih.nci.evs.api.query;

import static gov.nih.nci.evs.api.query.RawBoolQueries.getAllOfQuery;
import static gov.nih.nci.evs.api.query.RawBoolQueries.getAnyOfQuery;
import static gov.nih.nci.evs.api.query.RawBoolQueries.getMatchNothingQuery;

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
 * distance: 1 means direct children (or parents). With {@code isSelfIncluded}, the anchors
 * themselves also match, at distance 0.
 */
public abstract class HierarchyQuery extends EvsQuery {

  /** Field holding a document's identifier, which is the concept code. */
  static final String ID_FIELD = "_id";

  /** Metadata field naming the index a document is in; one index per terminology version. */
  static final String INDEX_FIELD = "_index";

  /** The smallest allowed {@code maxLevel}: direct children or parents. */
  static final int MIN_MAX_LEVEL = 1;

  /** A {@code maxLevel} of any distance, for constructors without a limit. */
  static final Integer ANY_LEVEL = null;

  /** For constructors whose anchors also match. */
  static final boolean WITH_SELF = true;

  /** For constructors whose anchors don't match (unless one is a relative of another). */
  static final boolean WITHOUT_SELF = false;

  /** Position of the anchor query's result in {@link #declareDependencies}'s list: the only one. */
  private static final int ANCHOR_QUERY_POSITION = 0;

  /** The query matching the anchor concepts. */
  private final EvsQuery anchorQuery;

  /** The maximum distance from the anchors, or null for any distance. */
  private final Integer maxLevel;

  /** Whether the anchors themselves match. */
  private final boolean isSelfIncluded;

  /**
   * Instantiates a {@link HierarchyQuery}.
   *
   * @param terminologies the terminologies being searched
   * @param anchorQuery the query matching the anchor concepts
   * @param maxLevel the maximum distance from the anchors, at least 1, or null for any distance
   * @param isSelfIncluded whether the anchors themselves match
   */
  protected HierarchyQuery(
      final List<Terminology> terminologies,
      final EvsQuery anchorQuery,
      final Integer maxLevel,
      final boolean isSelfIncluded) {
    super(terminologies);
    this.anchorQuery = Objects.requireNonNull(anchorQuery, "anchorQuery");

    final boolean hasInvalidMaxLevel = maxLevel != null && maxLevel < MIN_MAX_LEVEL;
    if (hasInvalidMaxLevel) {
      throw new IllegalArgumentException(
          "maxLevel must be at least " + MIN_MAX_LEVEL + ": " + maxLevel);
    }

    this.maxLevel = maxLevel;
    this.isSelfIncluded = isSelfIncluded;
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
  public boolean getIsSelfIncluded() {
    return isSelfIncluded;
  }

  /* see superclass */
  @Override
  public final QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final ConceptKeys anchors =
        declareDependencies(operations, anchorQuery).get(ANCHOR_QUERY_POSITION);

    final List<QueryBuilder> terminologyQueries = new ArrayList<>();
    for (final Terminology terminology : terminologies) {
      final Set<String> anchorCodes = getAnchorCodes(anchors, terminology);
      final boolean hasAnchors = !anchorCodes.isEmpty();
      if (hasAnchors) {
        terminologyQueries.add(fetchTerminologyQuery(operations, terminology, anchorCodes));
      }
    }

    final boolean hasTerminologyQueries = !terminologyQueries.isEmpty();
    return hasTerminologyQueries ? getAnyOfQuery(terminologyQueries) : getMatchNothingQuery();
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
  protected QueryBuilder getCodeInQuery(
      final OpenSearchOperations operations, final Set<String> codes) {
    final boolean hasCodes = !codes.isEmpty();
    if (!hasCodes) {
      return getMatchNothingQuery();
    }

    return new MatchAnyOf(terminologies, ID_FIELD, codes).fetchOpenSearchQuery(operations);
  }

  /**
   * Returns a query matching one concept by code, for the code convenience constructors.
   *
   * @param terminologies the terminologies being searched
   * @param code the code
   * @return the query
   */
  static EvsQuery getCodeQuery(final List<Terminology> terminologies, final String code) {
    return new Match(terminologies, ID_FIELD, Objects.requireNonNull(code, "code"));
  }

  /**
   * Returns the anchor codes found in one terminology version, or none.
   *
   * @param anchors the anchors found in every terminology version
   * @param terminology the terminology
   * @return the anchor codes
   */
  private static Set<String> getAnchorCodes(
      final ConceptKeys anchors, final Terminology terminology) {
    final ConceptKeys.TerminologyVersion terminologyVersion =
        new ConceptKeys.TerminologyVersion(terminology.getTerminology(), terminology.getVersion());
    return anchors.getCodesByTerminologyVersion().getOrDefault(terminologyVersion, Set.of());
  }

  /**
   * Returns the condition for one terminology: in that terminology's index, and a relative of its
   * anchors (or, with {@link #isSelfIncluded}, one of the anchors).
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @param anchorCodes the anchor codes found in that terminology
   * @return the condition
   */
  private QueryBuilder fetchTerminologyQuery(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Set<String> anchorCodes) {
    final QueryBuilder relativesQuery = fetchRelativesQuery(operations, terminology, anchorCodes);

    final QueryBuilder matchedQuery =
        isSelfIncluded
            ? getAnyOfQuery(List.of(relativesQuery, getCodeInQuery(operations, anchorCodes)))
            : relativesQuery;

    return getAllOfQuery(getInIndexQuery(operations, terminology), matchedQuery);
  }

  /**
   * Returns the condition matching documents in one terminology's index.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @return the condition
   */
  private QueryBuilder getInIndexQuery(
      final OpenSearchOperations operations, final Terminology terminology) {
    return new Match(terminologies, INDEX_FIELD, terminology.getIndexName())
        .fetchOpenSearchQuery(operations);
  }
}
