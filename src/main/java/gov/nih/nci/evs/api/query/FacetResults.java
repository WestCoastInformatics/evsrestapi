package gov.nih.nci.evs.api.query;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.opensearch.search.aggregations.Aggregation;
import org.opensearch.search.aggregations.Aggregations;
import org.opensearch.search.aggregations.bucket.MultiBucketsAggregation;
import org.opensearch.search.aggregations.bucket.SingleBucketAggregation;

/**
 * Reads facet counts from a search's aggregations: for each facet, each value and how many of the
 * current results have it. Works for every aggregation {@link FacetAggregations} builds, so a new
 * facet needs no reader of its own.
 */
public final class FacetResults {

  /** Orders values by count, most first, then by value, so results are repeatable. */
  private static final Comparator<Map.Entry<String, Long>> VALUE_ORDER =
      Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder())
          .thenComparing(Map.Entry.comparingByKey());

  /** Only static helpers. */
  private FacetResults() {}

  /**
   * Returns the counts of every facet in the aggregations.
   *
   * @param aggregations the aggregations, each one a facet
   * @return facet name to (value to count), values ordered by count, at most {@value
   *     FacetAggregations#MAX_FACET_VALUES} per facet
   */
  public static Map<String, Map<String, Long>> of(final Aggregations aggregations) {
    final Map<String, Map<String, Long>> countsByFacet = new LinkedHashMap<>();
    for (final Aggregation facet : aggregations) {
      countsByFacet.put(facet.getName(), getCounts(facet));
    }
    return countsByFacet;
  }

  /**
   * Returns one facet's counts, going through the nested and filter aggregations that wrap the
   * values.
   *
   * @param facet the facet's aggregation
   * @return value to count, ordered
   */
  private static Map<String, Long> getCounts(final Aggregation facet) {
    if (facet instanceof SingleBucketAggregation wrapper) {
      return getWrappedCounts(wrapper);
    }
    if (facet instanceof MultiBucketsAggregation values) {
      return getOrderedCounts(values.getBuckets());
    }
    return Map.of();
  }

  /**
   * Returns the counts inside a nested or filter aggregation.
   *
   * @param wrapper the wrapping aggregation
   * @return value to count, ordered
   */
  private static Map<String, Long> getWrappedCounts(final SingleBucketAggregation wrapper) {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final Aggregation inner : wrapper.getAggregations()) {
      counts.putAll(getCounts(inner));
    }
    return counts;
  }

  /**
   * Returns each bucket's value and concept count, without empty buckets, ordered and capped.
   *
   * @param buckets the buckets
   * @return value to count
   */
  private static Map<String, Long> getOrderedCounts(
      final List<? extends MultiBucketsAggregation.Bucket> buckets) {
    final Map<String, Long> counts = new LinkedHashMap<>();
    for (final MultiBucketsAggregation.Bucket bucket : buckets) {
      final long conceptCount = getConceptCount(bucket);
      final boolean hasConcepts = conceptCount > 0;
      if (hasConcepts) {
        counts.put(bucket.getKeyAsString(), conceptCount);
      }
    }
    return counts.entrySet().stream()
        .sorted(VALUE_ORDER)
        .limit(FacetAggregations.MAX_FACET_VALUES)
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (first, second) -> first,
                LinkedHashMap::new));
  }

  /**
   * Returns how many concepts a bucket counts: its concept count when its value sits in a nested
   * list (where the bucket itself counts entries), otherwise its own count.
   *
   * @param bucket the bucket
   * @return the concept count
   */
  private static long getConceptCount(final MultiBucketsAggregation.Bucket bucket) {
    final Aggregation conceptCount = bucket.getAggregations().get(FacetAggregations.CONCEPT_COUNT);
    if (conceptCount instanceof SingleBucketAggregation concepts) {
      return concepts.getDocCount();
    }
    return bucket.getDocCount();
  }
}
