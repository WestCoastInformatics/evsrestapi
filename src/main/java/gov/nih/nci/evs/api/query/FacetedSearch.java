package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.IncludeParam;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.client.orhlc.OpenSearchAggregations;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.filter.Filter;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.AggregationsContainer;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

/**
 * Runs a faceted search the standard way, so each facet keeps showing the values the user could
 * switch to:
 *
 * <ul>
 *   <li>The base query (typed text and fixed conditions) decides what every count starts from.
 *   <li>The selections (the user's picks in each facet) narrow only the hits returned, as a post
 *       filter. Picks in one facet are an {@link Or}; facets combine as AND.
 *   <li>Each facet is counted over the base query and every <em>other</em> facet's selection, not
 *       its own.
 * </ul>
 *
 * <p>Each selection's query is built once and used in the post filter and in the other facets'
 * counts, so any inner query it holds runs once.
 */
public final class FacetedSearch {

  /** Prefix of the aggregation holding one facet's counts, followed by the facet's position. */
  private static final String FACET_SCOPE_PREFIX = "facet";

  /** Only static helpers. */
  private FacetedSearch() {}

  /**
   * Runs the search and counts every facet.
   *
   * @param operations the OpenSearch operations
   * @param baseQuery the typed text and fixed conditions, which every count starts from
   * @param facets the facets to count, each with the user's picks, if any
   * @param page the page of hits to return
   * @param include which concept fields to return
   * @return the page, its total, and each facet's counts
   */
  public static FacetedSearchResult fetch(
      final OpenSearchOperations operations,
      final EvsQuery baseQuery,
      final List<Facet> facets,
      final Pageable page,
      final IncludeParam include) {
    final List<Optional<QueryBuilder>> selectionQueries = fetchSelectionQueries(operations, facets);
    final List<AbstractAggregationBuilder<?>> facetScopes =
        fetchFacetScopes(operations, facets, selectionQueries);

    final NativeSearchQueryBuilder search =
        new NativeSearchQueryBuilder()
            .withQuery(baseQuery.fetchOpenSearchQuery(operations))
            .withFilter(getAllOfQuery(selectionQueries, Optional.empty()))
            .withAggregations(facetScopes)
            .withPageable(page)
            .withTrackTotalHits(true)
            .withSourceFilter(
                new FetchSourceFilter(
                    true, include.getIncludedFields(), include.getExcludedFields()));
    final SearchHits<Concept> hits =
        operations.search(search.build(), Concept.class, baseQuery.getIndexCoordinates());

    final List<Concept> concepts =
        hits.getSearchHits().stream().map(SearchHit::getContent).toList();
    return new FacetedSearchResult(hits.getTotalHits(), concepts, getFacetCounts(hits));
  }

  /**
   * Builds each facet's selection query once, in facet order.
   *
   * @param operations the OpenSearch operations
   * @param facets the facets
   * @return each facet's selection query, or empty where nothing is picked
   */
  private static List<Optional<QueryBuilder>> fetchSelectionQueries(
      final OpenSearchOperations operations, final List<Facet> facets) {
    final List<Optional<QueryBuilder>> selectionQueries = new ArrayList<>();
    for (final Facet facet : facets) {
      selectionQueries.add(
          facet.selection().map(selection -> selection.fetchOpenSearchQuery(operations)));
    }
    return selectionQueries;
  }

  /**
   * Builds each facet's counts inside a filter on every other facet's selection.
   *
   * @param operations the OpenSearch operations
   * @param facets the facets
   * @param selectionQueries each facet's selection query
   * @return one scope aggregation per facet that has counts
   */
  private static List<AbstractAggregationBuilder<?>> fetchFacetScopes(
      final OpenSearchOperations operations,
      final List<Facet> facets,
      final List<Optional<QueryBuilder>> selectionQueries) {
    final List<AbstractAggregationBuilder<?>> facetScopes = new ArrayList<>();
    for (int position = 0; position < facets.size(); position++) {
      final List<AbstractAggregationBuilder<?>> counts =
          facets.get(position).dimension().fetchFacetBuilders(operations);
      final QueryBuilder otherSelections = getAllOfQuery(selectionQueries, Optional.of(position));
      facetScopes.add(getFacetScope(position, otherSelections, counts));
    }
    return facetScopes;
  }

  /**
   * Returns one facet's scope: a filter on the other selections holding the facet's counts.
   *
   * @param position the facet's position
   * @param otherSelections the other facets' selections
   * @param counts the facet's aggregations
   * @return the scope aggregation
   */
  private static AbstractAggregationBuilder<?> getFacetScope(
      final int position,
      final QueryBuilder otherSelections,
      final List<AbstractAggregationBuilder<?>> counts) {
    final AbstractAggregationBuilder<?> facetScope =
        AggregationBuilders.filter(FACET_SCOPE_PREFIX + position, otherSelections);
    counts.forEach(facetScope::subAggregation);
    return facetScope;
  }

  /**
   * Returns a query matching every selection, except the skipped facet's. With no selections it
   * matches everything.
   *
   * @param selectionQueries each facet's selection query
   * @param skippedPosition the facet whose selection to leave out, if any
   * @return the query
   */
  private static QueryBuilder getAllOfQuery(
      final List<Optional<QueryBuilder>> selectionQueries,
      final Optional<Integer> skippedPosition) {
    final BoolQueryBuilder allOf = QueryBuilders.boolQuery().filter(QueryBuilders.matchAllQuery());
    for (int position = 0; position < selectionQueries.size(); position++) {
      final boolean isSkipped = skippedPosition.equals(Optional.of(position));
      if (!isSkipped) {
        selectionQueries.get(position).ifPresent(allOf::filter);
      }
    }
    return allOf;
  }

  /**
   * Reads every facet's counts out of its scope, in facet order.
   *
   * @param hits the search hits
   * @return facet name to (value to count)
   */
  private static Map<String, Map<String, Long>> getFacetCounts(final SearchHits<Concept> hits) {
    final Map<String, Map<String, Long>> countsByFacet = new LinkedHashMap<>();
    final AggregationsContainer<?> aggregations = hits.getAggregations();
    final boolean hasAggregations = aggregations != null;
    if (!hasAggregations) {
      return countsByFacet;
    }

    final Aggregations facetScopes = ((OpenSearchAggregations) aggregations).aggregations();
    for (final Aggregation facetScope : facetScopes) {
      countsByFacet.putAll(FacetResults.of(((Filter) facetScope).getAggregations()));
    }
    return countsByFacet;
  }
}
