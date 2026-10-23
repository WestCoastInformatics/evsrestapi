package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.ConceptMinimal;
import gov.nih.nci.evs.api.model.Path;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

/**
 * Finds ancestors by reading the anchor concepts' stored paths to root. Every index stores {@code
 * paths}, whether or not it was indexed with ancestor fields, so this works on all of them. (The
 * {@code ancestors} field can't be read back: it's write-only, for matching only.)
 */
final class StoredPaths {

  /** Concepts fetched per lookup, so one lookup stays a reasonable size. */
  static final int CONCEPTS_PER_LOOKUP = 1_000;

  /** Field holding a concept's paths to root. */
  private static final String PATHS_FIELD = "paths";

  /** Field holding a concept's code. */
  private static final String CODE_FIELD = "code";

  /** Not instantiated. */
  private StoredPaths() {
    // n/a
  }

  /**
   * Returns the codes above the anchors in one terminology, within {@code maxLevel} levels by
   * shortest distance, not including the anchors unless one is above another.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology, whose index holds the paths
   * @param anchorCodes the anchor codes in that terminology
   * @param maxLevel the maximum distance above the anchors, or null for any distance
   * @return the codes above the anchors
   */
  static Set<String> fetchAncestorCodes(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Collection<String> anchorCodes,
      final Integer maxLevel) {
    // Shortest distance from any anchor to each ancestor, across every path of every anchor
    final Map<String, Integer> distances = new HashMap<>();
    for (final Concept anchor : fetchConcepts(operations, terminology, anchorCodes)) {
      if (anchor.getPaths() == null || anchor.getPaths().getPaths() == null) {
        continue;
      }
      for (final Path path : anchor.getPaths().getPaths()) {
        final List<ConceptMinimal> steps = path.getConcepts();
        for (int i = 0; i < steps.size(); i++) {
          final ConceptMinimal step = steps.get(i);
          final int distance = step.getLevel() != null ? step.getLevel() : i;
          if (distance > 0 && !step.getCode().equals(anchor.getCode())) {
            distances.merge(step.getCode(), distance, Math::min);
          }
        }
      }
    }
    final Set<String> codes =
        distances.entrySet().stream()
            .filter(e -> maxLevel == null || e.getValue() <= maxLevel)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    if (codes.size() > MatchAnyOf.MAX_VALUES) {
      throw new IllegalArgumentException(
          "Too many ancestors to match in one query: "
              + codes.size()
              + " > "
              + MatchAnyOf.MAX_VALUES);
    }
    return codes;
  }

  /**
   * Fetches concepts by code from one terminology's index, with only the code and paths, a batch at
   * a time.
   *
   * @param operations the OpenSearch operations
   * @param terminology the terminology
   * @param codes the codes
   * @return the concepts found
   */
  private static List<Concept> fetchConcepts(
      final OpenSearchOperations operations,
      final Terminology terminology,
      final Collection<String> codes) {
    final List<String> remaining = new ArrayList<>(codes);
    final List<Concept> concepts = new ArrayList<>();
    for (int start = 0; start < remaining.size(); start += CONCEPTS_PER_LOOKUP) {
      final List<String> batch =
          remaining.subList(start, Math.min(start + CONCEPTS_PER_LOOKUP, remaining.size()));
      final NativeSearchQuery lookup =
          new NativeSearchQueryBuilder()
              .withQuery(QueryBuilders.idsQuery().addIds(batch.toArray(new String[0])))
              .withSourceFilter(
                  new FetchSourceFilter(true, new String[] {CODE_FIELD, PATHS_FIELD}, null))
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
}
