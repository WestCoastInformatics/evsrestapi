package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.MatchNoneQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

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
 * <p>The inner query describes other concepts, so it runs first through {@link
 * #declareDependencies}. Terminologies indexed with ancestor fields then match with one query on
 * {@code ancestors} (or {@code ancestorDepths} for a depth limit). Older indexes fall back to
 * reading the stored hierarchy (see {@link StoredHierarchyFallback}).
 *
 * <p>Combining code is raw OpenSearch in small helpers ({@link #allOf}, {@link #anyOf}, {@link
 * #matchNothing}), to be replaced by the {@code And}, {@code Or}, and {@code Not} nodes when they
 * land.
 */
public class DescendantOf extends EvsQuery {

  /** Field listing every ancestor code of a concept. */
  static final String ANCESTORS_FIELD = "ancestors";

  /** Field listing "code|depth" tokens, each ancestor with its shortest distance. */
  static final String ANCESTOR_DEPTHS_FIELD = "ancestorDepths";

  /** Separator between code and depth in {@link #ANCESTOR_DEPTHS_FIELD}. */
  static final String DEPTH_SEPARATOR = "|";

  /** Field holding a document's identifier, which is the concept code. */
  static final String ID_FIELD = "_id";

  /** Metadata field naming the index a document is in; one index per terminology version. */
  static final String INDEX_FIELD = "_index";

  /** The query matching the anchor concepts. */
  private final EvsQuery ancestorQuery;

  /** The maximum depth below the anchors, or null for any depth. */
  private final Integer maxLevel;

  /** Whether the anchors themselves match. */
  private final boolean includeSelf;

  /**
   * Instantiates a {@link DescendantOf} for everything below the concepts an inner query matches.
   *
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   */
  public DescendantOf(final List<Terminology> terminologies, final EvsQuery ancestorQuery) {
    this(terminologies, ancestorQuery, null, false);
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
    this(terminologies, ancestorQuery, maxLevel, false);
  }

  /**
   * Instantiates a {@link DescendantOf} for everything below one concept.
   *
   * @param terminologies the terminologies being searched
   * @param code the anchor concept's code
   */
  public DescendantOf(final List<Terminology> terminologies, final String code) {
    this(terminologies, codeQuery(terminologies, code), null, false);
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
    this(terminologies, codeQuery(terminologies, code), maxLevel, false);
  }

  /**
   * Instantiates a {@link DescendantOf}, optionally matching the anchors themselves. For {@link
   * DescendantOrSelfOf}.
   *
   * @param terminologies the terminologies being searched
   * @param ancestorQuery the query matching the anchor concepts
   * @param maxLevel the maximum depth below the anchors, at least 1, or null for any depth
   * @param includeSelf whether the anchors themselves match
   */
  protected DescendantOf(
      final List<Terminology> terminologies,
      final EvsQuery ancestorQuery,
      final Integer maxLevel,
      final boolean includeSelf) {
    super(terminologies);
    this.ancestorQuery = Objects.requireNonNull(ancestorQuery, "ancestorQuery");
    if (maxLevel != null && maxLevel < 1) {
      throw new IllegalArgumentException("maxLevel must be at least 1: " + maxLevel);
    }
    this.maxLevel = maxLevel;
    this.includeSelf = includeSelf;
  }

  /**
   * Returns the query matching the anchor concepts.
   *
   * @return the ancestor query
   */
  public EvsQuery getAncestorQuery() {
    return ancestorQuery;
  }

  /**
   * Returns the maximum depth below the anchors.
   *
   * @return the max level, or null for any depth
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
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final ConceptKeys anchors = declareDependencies(operations, ancestorQuery).get(0);

    // Codes are only unique within one terminology version, and each version has its own index,
    // so match each terminology's anchors only inside that terminology's index
    final List<QueryBuilder> perTerminology = new ArrayList<>();
    for (final Terminology terminology : terminologies) {
      final Set<String> anchorCodes =
          anchors
              .getCodesByTerminologyVersion()
              .getOrDefault(
                  new ConceptKeys.TerminologyVersion(
                      terminology.getTerminology(), terminology.getVersion()),
                  Set.of());
      if (!anchorCodes.isEmpty()) {
        perTerminology.add(
            allOf(
                inIndexOf(operations, terminology),
                belowOrAtAnchors(operations, terminology, anchorCodes)));
      }
    }
    return perTerminology.isEmpty() ? matchNothing() : anyOf(perTerminology);
  }

  /**
   * Returns the condition for concepts below the anchors (and the anchors, if included) in one
   * terminology.
   *
   * @param operations the OpenSearch operations, for the fallback
   * @param terminology the terminology
   * @param anchorCodes the anchor codes in that terminology
   * @return the condition
   */
  private QueryBuilder belowOrAtAnchors(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Set<String> anchorCodes) {
    final QueryBuilder below =
        hasAncestorFields(terminology)
            ? belowAnchorsIndexed(operations, anchorCodes)
            // TODO: Remove the fallback once every terminology is reindexed with ancestor fields
            : belowAnchorsFallback(operations, terminology, anchorCodes);
    return includeSelf ? anyOf(List.of(below, codeIn(operations, anchorCodes))) : below;
  }

  /**
   * Returns the condition for concepts below the anchors, using the indexed ancestor fields.
   *
   * @param operations the OpenSearch operations
   * @param anchorCodes the anchor codes
   * @return the condition
   */
  private QueryBuilder belowAnchorsIndexed(
      final OpenSearchOperations operations, final Set<String> anchorCodes) {
    if (maxLevel == null) {
      return new MatchAnyOf(terminologies, ANCESTORS_FIELD, anchorCodes)
          .fetchOpenSearchQuery(operations);
    }
    return new MatchAnyOf(terminologies, ANCESTOR_DEPTHS_FIELD, depthTokens(anchorCodes, maxLevel))
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
  private QueryBuilder belowAnchorsFallback(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Set<String> anchorCodes) {
    final Set<String> descendantCodes =
        StoredHierarchyFallback.fetchDescendantCodes(
            operations, terminology, anchorCodes, maxLevel);
    return descendantCodes.isEmpty() ? matchNothing() : codeIn(operations, descendantCodes);
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
  private static boolean hasAncestorFields(final Terminology terminology) {
    return terminology.getMetadata() != null
        && Boolean.TRUE.equals(terminology.getMetadata().getHierarchyFields());
  }

  /**
   * Returns "code|depth" tokens for every anchor and every depth from 1 to {@code maxLevel}, which
   * match {@link #ANCESTOR_DEPTHS_FIELD}. For example C3262 with depth 2 gives C3262|1 and C3262|2.
   *
   * @param anchorCodes the anchor codes
   * @param maxLevel the maximum depth
   * @return the tokens
   */
  static List<String> depthTokens(final Collection<String> anchorCodes, final int maxLevel) {
    if ((long) anchorCodes.size() * maxLevel > MatchAnyOf.MAX_VALUES) {
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
      for (int depth = 1; depth <= maxLevel; depth++) {
        tokens.add(code + DEPTH_SEPARATOR + depth);
      }
    }
    return tokens;
  }

  /**
   * Returns a query matching one concept by code.
   *
   * @param terminologies the terminologies being searched
   * @param code the code
   * @return the query
   */
  static EvsQuery codeQuery(final List<Terminology> terminologies, final String code) {
    return new Match(terminologies, ID_FIELD, Objects.requireNonNull(code, "code"));
  }

  /**
   * Returns the condition matching concepts whose code is in the set. A concept's document id is
   * its code; the {@code code} field itself is analyzed text, so it can't be matched exactly.
   *
   * @param operations the OpenSearch operations
   * @param codes the codes
   * @return the condition
   */
  private QueryBuilder codeIn(final OpenSearchOperations operations, final Set<String> codes) {
    return new MatchAnyOf(terminologies, ID_FIELD, codes).fetchOpenSearchQuery(operations);
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
   * Combines conditions that must all match. To become the {@code And} node.
   *
   * @param conditions the conditions
   * @return the combined condition
   */
  private static QueryBuilder allOf(final QueryBuilder... conditions) {
    final BoolQueryBuilder bool = QueryBuilders.boolQuery();
    for (final QueryBuilder condition : conditions) {
      bool.must(condition);
    }
    return bool;
  }

  /**
   * Combines conditions of which at least one must match. To become the {@code Or} node.
   *
   * @param conditions the conditions
   * @return the combined condition
   */
  private static QueryBuilder anyOf(final List<QueryBuilder> conditions) {
    final BoolQueryBuilder bool = QueryBuilders.boolQuery().minimumShouldMatch(1);
    conditions.forEach(bool::should);
    return bool;
  }

  /**
   * Returns a condition no document matches, e.g. when the inner query found no anchors. To become
   * a {@code Not} of match-all, or a constant node.
   *
   * @return the condition
   */
  private static QueryBuilder matchNothing() {
    return new MatchNoneQueryBuilder();
  }
}
