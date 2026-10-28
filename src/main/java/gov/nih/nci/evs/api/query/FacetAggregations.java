package gov.nih.nci.evs.api.query;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;
import org.opensearch.search.aggregations.AggregationBuilders;
import org.opensearch.search.aggregations.BucketOrder;
import org.opensearch.search.aggregations.bucket.filter.FiltersAggregator.KeyedFilter;

/**
 * Builds the aggregations behind facets, so each node's {@code fetchFacetBuilders} is one line. A
 * facet counts, over the current results, how many concepts have each value of one field (see
 * {@link FacetResults}).
 *
 * <p>Values are counted per concept, not per entry: aggregations inside a nested field count back
 * to the concept with a {@code reverse_nested} sub-aggregation, which {@link FacetResults} reads.
 */
final class FacetAggregations {

  /** How many values a facet returns at most. */
  static final int MAX_FACET_VALUES = 50;

  /** Name of the sub-aggregation counting concepts rather than nested entries. */
  static final String CONCEPT_COUNT = "concepts";

  /** Name of the filter, inside a nested aggregation, keeping only the facet's entries. */
  private static final String ENTRY_FILTER = "entries";

  /** Name of the terms aggregation, inside the entry filter, counting each value. */
  private static final String VALUES = "values";

  /** Characters OpenSearch doesn't allow in aggregation names. */
  private static final String DISALLOWED_NAME_CHARACTERS = "[\\[\\]>]";

  /** Separates a facet's dimension from its key in its name, e.g. "roles:R101". */
  private static final String NAME_SEPARATOR = ":";

  /** Separates several keys in one name, e.g. the anchors of a branch facet. */
  private static final String KEY_SEPARATOR = ",";

  /** Only static helpers. */
  private FacetAggregations() {}

  /**
   * Returns a facet counting each value of a keyword field.
   *
   * @param name the facet name
   * @param field the keyword field
   * @return the aggregation
   */
  static AbstractAggregationBuilder<?> getTermsFacet(final String name, final String field) {
    return AggregationBuilders.terms(name)
        .field(field)
        .size(MAX_FACET_VALUES)
        .order(getValueOrder());
  }

  /**
   * Returns a facet counting each value of a field inside a nested list, only over the entries the
   * filter keeps, e.g. the values of {@code properties} entries whose type is Semantic_Type.
   *
   * @param name the facet name
   * @param path the nested list field, e.g. "properties"
   * @param entryFilter the condition an entry must meet to be counted
   * @param field the keyword field inside the list, e.g. "properties.value"
   * @return the aggregation
   */
  static AbstractAggregationBuilder<?> getNestedTermsFacet(
      final String name, final String path, final QueryBuilder entryFilter, final String field) {
    final AbstractAggregationBuilder<?> valuesByConcept =
        AggregationBuilders.terms(VALUES)
            .field(field)
            .size(MAX_FACET_VALUES)
            .order(getValueOrder())
            .subAggregation(AggregationBuilders.reverseNested(CONCEPT_COUNT));
    return AggregationBuilders.nested(name, path)
        .subAggregation(
            AggregationBuilders.filter(ENTRY_FILTER, entryFilter).subAggregation(valuesByConcept));
  }

  /**
   * Returns a facet counting, for each code, the concepts that are that concept or below it.
   *
   * @param name the facet name
   * @param ancestorsField the field listing each concept's ancestor codes
   * @param codes the codes to count under
   * @return the aggregation
   */
  static AbstractAggregationBuilder<?> getBranchesFacet(
      final String name, final String ancestorsField, final Collection<String> codes) {
    final KeyedFilter[] branchFilters =
        new TreeSet<>(codes)
            .stream()
                .map(code -> new KeyedFilter(code, getAtOrBelowQuery(ancestorsField, code)))
                .toArray(KeyedFilter[]::new);
    return AggregationBuilders.filters(name, branchFilters);
  }

  /**
   * Returns the children's facets, each name once: identical conditions in several children give
   * one facet, and OpenSearch rejects two aggregations with one name.
   *
   * @param operations the OpenSearch operations
   * @param children the child queries
   * @return the facet aggregations
   */
  static List<AbstractAggregationBuilder<?>> fetchChildFacetBuilders(
      final OpenSearchOperations operations, final List<EvsQuery> children) {
    final Map<String, AbstractAggregationBuilder<?>> facetsByName = new LinkedHashMap<>();
    for (final EvsQuery child : children) {
      addFacetsNotYetAdded(facetsByName, child.fetchFacetBuilders(operations));
    }
    return List.copyOf(facetsByName.values());
  }

  /**
   * Adds the facets whose names haven't been added yet.
   *
   * @param facetsByName the facets so far, by name
   * @param facets the facets to add
   */
  private static void addFacetsNotYetAdded(
      final Map<String, AbstractAggregationBuilder<?>> facetsByName,
      final List<AbstractAggregationBuilder<?>> facets) {
    for (final AbstractAggregationBuilder<?> facet : facets) {
      facetsByName.putIfAbsent(facet.getName(), facet);
    }
  }

  /**
   * Returns a facet name: the dimension, then the key telling facets of one dimension apart.
   *
   * @param dimension the dimension, e.g. "roles"
   * @param key the key, e.g. the role type
   * @return the name, with characters OpenSearch doesn't allow replaced
   */
  static String getFacetName(final String dimension, final String key) {
    final String name = dimension + NAME_SEPARATOR + key;
    return name.replaceAll(DISALLOWED_NAME_CHARACTERS, "_");
  }

  /**
   * Returns a readable key for a value match: the term of an exact match, the sorted terms of a
   * one-of match, or the match's own description otherwise.
   *
   * @param match the value match
   * @return the key
   */
  static String getMatchKey(final ValueMatch match) {
    if (match instanceof Exact exact) {
      return exact.term();
    }
    if (match instanceof OneOf oneOf) {
      return getCodesKey(oneOf.terms());
    }
    return match.toString();
  }

  /**
   * Returns a key listing codes in sorted order.
   *
   * @param codes the codes
   * @return the key
   */
  static String getCodesKey(final Collection<String> codes) {
    return new TreeSet<>(codes).stream().collect(Collectors.joining(KEY_SEPARATOR));
  }

  /**
   * Returns the facet value order: most concepts first, then by value, so results are repeatable.
   *
   * @return the order
   */
  private static BucketOrder getValueOrder() {
    return BucketOrder.compound(List.of(BucketOrder.count(false), BucketOrder.key(true)));
  }

  /**
   * Returns the query matching the concept with this code and every concept below it.
   *
   * @param ancestorsField the field listing each concept's ancestor codes
   * @param code the code
   * @return the query
   */
  private static QueryBuilder getAtOrBelowQuery(final String ancestorsField, final String code) {
    return QueryBuilders.boolQuery()
        .should(QueryBuilders.termQuery(ancestorsField, code))
        .should(QueryBuilders.idsQuery().addIds(code))
        .minimumShouldMatch(1);
  }
}
