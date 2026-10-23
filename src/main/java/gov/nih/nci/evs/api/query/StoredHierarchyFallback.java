package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

/**
 * Finds descendants for terminologies indexed before concepts carried {@code ancestors} and {@code
 * ancestorDepths} (their metadata lacks {@code hierarchyFields}). Those indexes can't match "below
 * X" in one query, so this reads the hierarchy stored on the anchor concepts and the hierarchy
 * nodes match the codes it returns.
 *
 * <p>TODO: Remove this class once every terminology is reindexed with ancestor fields. The indexed
 * path in {@link DescendantOf} then covers everything.
 */
final class StoredHierarchyFallback {

  /** Concepts fetched per lookup, so one lookup stays a reasonable size. */
  static final int CONCEPTS_PER_LOOKUP = 1_000;

  /** Field holding a concept's precomputed descendants (a depth-first list). */
  private static final String DESCENDANTS_FIELD = "descendants";

  /** Field holding a concept's direct children. */
  private static final String CHILDREN_FIELD = "children";

  /** Field holding a concept's code. */
  private static final String CODE_FIELD = "code";

  /** Not instantiated. */
  private StoredHierarchyFallback() {
    // n/a
  }

  /**
   * Returns the codes below the anchors in one terminology, not including the anchors unless one is
   * below another.
   *
   * <p>Without a depth limit, this reads each anchor's precomputed descendant list. With one, it
   * walks children breadth-first from all anchors at once: the precomputed list records the depth
   * at which a depth-first walk first reached a concept, which under polyhierarchy can be larger
   * than the shortest distance, so filtering it by depth could miss concepts.
   *
   * <p>TODO: Remove with this class once every terminology is reindexed with ancestor fields.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology, whose index holds the stored hierarchy
   * @param anchorCodes the anchor codes in that terminology
   * @param maxLevel the maximum depth below the anchors, or null for any depth
   * @return the codes below the anchors
   */
  static Set<String> fetchDescendantCodes(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Collection<String> anchorCodes,
      final Integer maxLevel) {
    return maxLevel == null
        ? fetchAllDescendantCodes(operations, terminology, anchorCodes)
        : fetchDescendantCodesWithinLevel(operations, terminology, anchorCodes, maxLevel);
  }

  /**
   * Returns every code in the anchors' precomputed descendant lists.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @param anchorCodes the anchor codes
   * @return the descendant codes
   */
  private static Set<String> fetchAllDescendantCodes(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Collection<String> anchorCodes) {
    final Set<String> codes = new HashSet<>();
    for (final Concept anchor :
        fetchConcepts(operations, terminology, anchorCodes, DESCENDANTS_FIELD)) {
      anchor.getDescendants().forEach(descendant -> codes.add(descendant.getCode()));
      checkSize(codes);
    }
    return codes;
  }

  /**
   * Returns the codes within {@code maxLevel} levels below the anchors, by shortest distance,
   * walking children one level at a time from all anchors at once.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @param anchorCodes the anchor codes
   * @param maxLevel the maximum depth below the anchors
   * @return the descendant codes
   */
  private static Set<String> fetchDescendantCodesWithinLevel(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Collection<String> anchorCodes,
      final int maxLevel) {
    final Set<String> codes = new HashSet<>();
    Set<String> level = new HashSet<>(anchorCodes);
    for (int depth = 1; depth <= maxLevel && !level.isEmpty(); depth++) {
      final Set<String> nextLevel = new HashSet<>();
      for (final Concept parent : fetchConcepts(operations, terminology, level, CHILDREN_FIELD)) {
        for (final Concept child : parent.getChildren()) {
          // First time reached is the shortest distance; skip codes already seen
          if (codes.add(child.getCode())) {
            nextLevel.add(child.getCode());
          }
        }
      }
      checkSize(codes);
      level = nextLevel;
    }
    return codes;
  }

  /**
   * Fetches concepts by code from one terminology's index, with only the code and one hierarchy
   * field, a batch at a time.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @param codes the codes
   * @param hierarchyField the hierarchy field to fetch
   * @return the concepts found
   */
  private static List<Concept> fetchConcepts(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Collection<String> codes,
      final String hierarchyField) {
    final List<String> remaining = new ArrayList<>(codes);
    final List<Concept> concepts = new ArrayList<>();
    for (int start = 0; start < remaining.size(); start += CONCEPTS_PER_LOOKUP) {
      final List<String> batch =
          remaining.subList(start, Math.min(start + CONCEPTS_PER_LOOKUP, remaining.size()));
      final NativeSearchQuery lookup =
          new NativeSearchQueryBuilder()
              .withQuery(QueryBuilders.idsQuery().addIds(batch.toArray(new String[0])))
              .withSourceFilter(
                  new FetchSourceFilter(true, new String[] {CODE_FIELD, hierarchyField}, null))
              .withPageable(PageRequest.of(0, batch.size()))
              .build();
      operations
          .search(lookup, Concept.class, IndexCoordinates.of(terminology.getIndexName()))
          .getSearchHits()
          .stream()
          .map(SearchHit::getContent)
          .forEach(concepts::add);
    }
    return concepts;
  }

  /**
   * Rejects expansions too large to match in one {@code terms} query.
   *
   * @param codes the codes found so far
   */
  private static void checkSize(final Set<String> codes) {
    if (codes.size() > MatchAnyOf.MAX_VALUES) {
      throw new IllegalArgumentException(
          "Branch is too large for an index without ancestor fields (more than "
              + MatchAnyOf.MAX_VALUES
              + " concepts); reindex the terminology to support it");
    }
  }
}
