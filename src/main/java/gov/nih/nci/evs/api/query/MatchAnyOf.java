package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;

/**
 * Matches documents whose field equals any of a set of values (case sensitive, whole value), e.g.
 * concepts whose {@code ancestors} include any of several codes. The many-value form of {@link
 * Exact}: one OpenSearch {@code terms} query instead of a {@code should} clause per value, which
 * would hit OpenSearch's clause limit for large sets.
 *
 * <p>OpenSearch accepts at most {@value #MAX_VALUES} values in one {@code terms} query by default,
 * so the constructor rejects larger sets. Callers that build the set from other concepts (see
 * {@link EvsQuery#declareDependencies}) check the size first to report a clearer error.
 */
public class MatchAnyOf extends EvsQuery {

  /** OpenSearch's default limit on values in one {@code terms} query. */
  public static final int MAX_VALUES = 65_536;

  /** The field to match against. */
  private final String field;

  /** The values, any of which the field may equal. Sorted, so built queries are repeatable. */
  private final Set<String> values;

  /**
   * Instantiates a {@link MatchAnyOf}.
   *
   * @param terminologies the terminologies being searched
   * @param field the field to match against
   * @param values the values, any of which the field may equal
   */
  public MatchAnyOf(
      final List<Terminology> terminologies, final String field, final Collection<String> values) {
    super(terminologies);
    this.field = Objects.requireNonNull(field, "field");
    this.values = new TreeSet<>(Objects.requireNonNull(values, "values"));

    final boolean hasTooManyValues = this.values.size() > MAX_VALUES;
    if (hasTooManyValues) {
      throw new IllegalArgumentException(
          "Too many values for one terms query on '"
              + field
              + "': "
              + this.values.size()
              + " > "
              + MAX_VALUES);
    }
  }

  /**
   * Returns the field to match against.
   *
   * @return the field
   */
  public String getField() {
    return field;
  }

  /**
   * Returns the values, any of which the field may equal.
   *
   * @return the values, sorted and unmodifiable
   */
  public Set<String> getValues() {
    return Collections.unmodifiableSet(values);
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    return QueryBuilders.termsQuery(field, values);
  }

  /**
   * Returns this node's facet, named after its field: how many concepts have each value of the
   * field, whichever values this node selects.
   *
   * @param operations the OpenSearch operations
   * @return the facet aggregation
   */
  @Override
  public List<AbstractAggregationBuilder<?>> fetchFacetBuilders(
      final OpenSearchOperations operations) {
    return List.of(FacetAggregations.getTermsFacet(field, field));
  }
}
