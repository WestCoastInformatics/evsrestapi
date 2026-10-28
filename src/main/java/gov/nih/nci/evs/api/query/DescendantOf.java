package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.model.TerminologyMetadata;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;

/**
 * Matches concepts strictly below any concept an inner query matches, in the same terminology
 * version. For example, everything below Melanoma, or everything below any concept named "...
 * Carcinoma". The inner query can be any {@link EvsQuery}, including another hierarchy node, so
 * these nest: the descendants of the children of X is {@code new DescendantOf(t, new
 * DescendantOf(t, x, 1))}.
 *
 * <p>An optional {@code maxLevel} limits how far below: 1 means direct children. Depth is the
 * shortest distance, so a concept that is a direct child of X on one path and deeper on another
 * counts as depth 1.
 *
 * <p>Terminologies indexed with ancestor fields match with one query on {@code ancestors} (or
 * {@code ancestorDepths} for a depth limit). Older indexes fall back to reading the stored
 * hierarchy (see {@link StoredHierarchyFallback}).
 *
 * @see DescendantOrSelfOf
 * @see AncestorOf
 */
public class DescendantOf extends HierarchyQuery {

  /** Field listing every ancestor code of a concept. */
  static final String ANCESTORS_FIELD = "ancestors";

  /** Field listing "code|depth" tokens, each ancestor with its shortest distance. */
  static final String ANCESTOR_DEPTHS_FIELD = "ancestorDepths";

  /** Separator between code and depth in {@link #ANCESTOR_DEPTHS_FIELD}. */
  static final String DEPTH_SEPARATOR = "|";

  /** The first depth below an anchor: its direct children. */
  private static final int FIRST_DEPTH = 1;

  /** The dimension of this node's facet. */
  private static final String BRANCH_DIMENSION = "branch";

  /** Position of the anchors among the facet's dependencies. */
  private static final int ANCHORS_POSITION = 0;

  /** Position of the anchors' children among the facet's dependencies. */
  private static final int CHILDREN_POSITION = 1;

  /**
   * Instantiates a {@link DescendantOf} for everything below the concepts an inner query matches.
   *
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   */
  public DescendantOf(final List<Terminology> terminologies, final EvsQuery ancestorQuery) {
    super(terminologies, ancestorQuery, ANY_LEVEL, WITHOUT_SELF);
  }

  /**
   * Instantiates a {@link DescendantOf} for concepts within {@code maxLevel} levels below the
   * concepts an inner query matches.
   *
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   * @param maxLevel the maximum depth below the anchors, at least 1
   */
  public DescendantOf(
      final List<Terminology> terminologies, final EvsQuery ancestorQuery, final int maxLevel) {
    super(terminologies, ancestorQuery, maxLevel, WITHOUT_SELF);
  }

  /**
   * Instantiates a {@link DescendantOf} for everything below one concept.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   */
  public DescendantOf(final List<Terminology> terminologies, final String code) {
    super(terminologies, getCodeQuery(terminologies, code), ANY_LEVEL, WITHOUT_SELF);
  }

  /**
   * Instantiates a {@link DescendantOf} for concepts within {@code maxLevel} levels below one
   * concept.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   * @param maxLevel the maximum depth below the anchor, at least 1
   */
  public DescendantOf(
      final List<Terminology> terminologies, final String code, final int maxLevel) {
    super(terminologies, getCodeQuery(terminologies, code), maxLevel, WITHOUT_SELF);
  }

  /**
   * Instantiates a {@link DescendantOf}, optionally matching the anchors themselves. For {@link
   * DescendantOrSelfOf}.
   *
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   * @param maxLevel the maximum depth below the anchors, at least 1, or null for any depth
   * @param isSelfIncluded whether the anchors themselves match
   */
  protected DescendantOf(
      final List<Terminology> terminologies,
      final EvsQuery ancestorQuery,
      final Integer maxLevel,
      final boolean isSelfIncluded) {
    super(terminologies, ancestorQuery, maxLevel, isSelfIncluded);
  }

  /* see superclass */
  @Override
  protected QueryBuilder fetchRelativesQuery(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Set<String> anchorCodes) {
    final boolean hasAncestorFields = getHasAncestorFields(terminology);
    if (hasAncestorFields) {
      return getBelowAnchorsQuery(operations, anchorCodes);
    }

    // TODO: Remove the fallback once every terminology is reindexed with ancestor fields
    return fetchBelowAnchorsFallbackQuery(operations, terminology, anchorCodes);
  }

  /**
   * Returns this node's facet, to drill down one level: for each child of the anchors, how many
   * concepts are that child or below it. E.g. for C3262 Neoplasm, one count each for Neoplasm by
   * Morphology, by Site, and by Special Category. Named after the anchors, e.g. "branch:C3262".
   *
   * <p>Counts use {@link #ANCESTORS_FIELD}, so on an index without the ancestor fields the facet is
   * empty.
   *
   * @param operations the OpenSearch operations, for finding the anchors and their children
   * @return the facet aggregation
   */
  @Override
  public List<AbstractAggregationBuilder<?>> fetchFacetBuilders(
      final OpenSearchOperations operations) {
    final DescendantOf childrenOfAnchors =
        new DescendantOf(terminologies, getAnchorQuery(), FIRST_DEPTH);
    final List<ConceptKeys> anchorsAndChildren =
        declareDependencies(operations, getAnchorQuery(), childrenOfAnchors);
    final Set<String> anchorCodes = getCodes(anchorsAndChildren.get(ANCHORS_POSITION));
    final Set<String> childCodes = getCodes(anchorsAndChildren.get(CHILDREN_POSITION));

    final String facetName =
        FacetAggregations.getFacetName(
            BRANCH_DIMENSION, FacetAggregations.getCodesKey(anchorCodes));
    return List.of(FacetAggregations.getBranchesFacet(facetName, ANCESTORS_FIELD, childCodes));
  }

  /**
   * Returns the codes found in any terminology version.
   *
   * @param keys the concept keys
   * @return the codes
   */
  private static Set<String> getCodes(final ConceptKeys keys) {
    return keys.getCodesByTerminologyVersion().values().stream()
        .flatMap(Set::stream)
        .collect(Collectors.toCollection(TreeSet::new));
  }

  /**
   * Returns "code|depth" tokens for every anchor and every depth from 1 to {@code maxLevel}, which
   * match {@link #ANCESTOR_DEPTHS_FIELD}. For example C3262 with depth 2 gives C3262|1 and C3262|2.
   *
   * @param anchorCodes the anchor codes
   * @param maxLevel the maximum depth
   * @return the tokens
   */
  static List<String> getDepthTokens(final Collection<String> anchorCodes, final int maxLevel) {
    final long tokenCount = (long) anchorCodes.size() * maxLevel;
    final boolean hasTooManyTokens = tokenCount > MatchAnyOf.MAX_VALUES;
    if (hasTooManyTokens) {
      throw new IllegalArgumentException(
          "Too many anchors for maxLevel "
              + maxLevel
              + ": "
              + anchorCodes.size()
              + " anchors gives more than "
              + MatchAnyOf.MAX_VALUES
              + " depth tokens");
    }

    final List<String> tokens = new ArrayList<>();
    for (final String code : anchorCodes) {
      tokens.addAll(getAnchorDepthTokens(code, maxLevel));
    }

    return tokens;
  }

  /**
   * Returns the "code|depth" tokens for one anchor and every depth from 1 to {@code maxLevel}.
   *
   * @param code the anchor code
   * @param maxLevel the maximum depth
   * @return the tokens
   */
  private static List<String> getAnchorDepthTokens(final String code, final int maxLevel) {
    final List<String> tokens = new ArrayList<>();
    for (int depth = FIRST_DEPTH; depth <= maxLevel; depth++) {
      tokens.add(code + DEPTH_SEPARATOR + depth);
    }

    return tokens;
  }

  /**
   * Returns the condition for concepts below the anchors, using the indexed ancestor fields.
   *
   * @param operations the OpenSearch operations
   * @param anchorCodes the anchor codes
   * @return the condition
   */
  private QueryBuilder getBelowAnchorsQuery(
      final OpenSearchOperations operations, final Set<String> anchorCodes) {
    final boolean hasLevelLimit = getMaxLevel() != null;
    if (!hasLevelLimit) {
      return new MatchAnyOf(terminologies, ANCESTORS_FIELD, anchorCodes)
          .fetchOpenSearchQuery(operations);
    }

    return new MatchAnyOf(
            terminologies, ANCESTOR_DEPTHS_FIELD, getDepthTokens(anchorCodes, getMaxLevel()))
        .fetchOpenSearchQuery(operations);
  }

  /**
   * Returns the condition for concepts below the anchors in an index without ancestor fields, by
   * expanding the anchors from the stored hierarchy.
   *
   * <p>TODO: Remove once every terminology is reindexed with ancestor fields.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @param anchorCodes the anchor codes
   * @return the condition
   */
  private QueryBuilder fetchBelowAnchorsFallbackQuery(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Set<String> anchorCodes) {
    final Set<String> descendantCodes =
        StoredHierarchyFallback.fetchDescendantCodes(
            operations, terminology, anchorCodes, getMaxLevel());

    return getCodeInQuery(operations, descendantCodes);
  }

  /**
   * Indicates whether a terminology's index has the {@code ancestors} and {@code ancestorDepths}
   * fields.
   *
   * <p>TODO: Remove with the fallback; every index will have them.
   *
   * @param terminology the terminology
   * @return true if the fields are indexed
   */
  private static boolean getHasAncestorFields(final Terminology terminology) {
    final TerminologyMetadata metadata = terminology.getMetadata();
    final boolean hasMetadata = metadata != null;
    if (!hasMetadata) {
      return false;
    }

    return Boolean.TRUE.equals(metadata.getHierarchyFields());
  }
}
