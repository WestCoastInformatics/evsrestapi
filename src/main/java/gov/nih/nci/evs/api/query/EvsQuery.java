package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.IncludeParam;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.sort.SortBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

/**
 * A node in an EVS search query tree. Atomic queries match a single field with a single strategy;
 * composite queries combine other {@link EvsQuery} nodes.
 *
 * <p>EXPERIMENTAL: nodes may also contribute sorts (see {@link #getSorts()}), and any node can be
 * run as the outermost query of a search request (see {@link #getNativeSearchQuery}).
 */
public abstract class EvsQuery {

  /**
   * Metadata field naming the index a hit came from. Each terminology has its own concept index, so
   * this orders by terminology. The terminology field itself is analyzed text and can't be sorted
   * on.
   */
  private static final String INDEX_FIELD = "_index";

  /** Field that orders concepts within a terminology. */
  private static final String CODE_FIELD = "code";

  /** The terminologies being searched. Unmodifiable. */
  protected final List<Terminology> terminologies;

  /**
   * Instantiates an {@link EvsQuery}.
   *
   * @param terminologies the terminologies being searched
   */
  protected EvsQuery(final List<Terminology> terminologies) {
    this.terminologies = List.copyOf(terminologies);
  }

  /**
   * Returns the terminologies.
   *
   * @return the terminologies
   */
  public List<Terminology> getTerminologies() {
    return terminologies;
  }

  /**
   * Returns the OpenSearch query for this node.
   *
   * @return the query builder
   */
  public abstract QueryBuilder getQueryBuilder();

  /**
   * EXPERIMENTAL: returns the sorts this node wants applied, in priority order. Most nodes only
   * match, so the default is none.
   *
   * <p>Composite nodes must return their own sorts first, then each child's sorts in child order. A
   * nested node's sorts therefore only break ties left by the nodes above and before it. A node
   * that negates its children (e.g. {@code Not}) contributes none of their sorts.
   *
   * @return the sorts, highest priority first
   */
  public List<SortBuilder<?>> getSorts() {
    return List.of();
  }

  /**
   * EXPERIMENTAL: builds a search request with this node as the outermost query. Sorts come from
   * {@link #getSorts()}, with duplicates dropped, followed by terminology and code so results are
   * stable between requests.
   *
   * @param pageable the page to return
   * @param include the fields to return for each hit
   * @return the search request
   */
  public final NativeSearchQuery getNativeSearchQuery(
      final Pageable pageable, final IncludeParam include) {
    return new NativeSearchQueryBuilder()
        .withQuery(getQueryBuilder())
        .withSorts(getSortsWithTieBreak())
        .withSourceFilter(
            new FetchSourceFilter(true, include.getIncludedFields(), include.getExcludedFields()))
        .withPageable(pageable)
        .build();
  }

  /**
   * Returns the concept indexes of the terminologies, for running {@link #getNativeSearchQuery}.
   *
   * @return the index coordinates
   */
  public IndexCoordinates getIndexCoordinates() {
    return IndexCoordinates.of(
        terminologies.stream().map(Terminology::getIndexName).toArray(String[]::new));
  }

  /**
   * Returns this node's sorts without duplicates, then the terminology and code tie-break.
   *
   * @return the sorts, highest priority first
   */
  private List<SortBuilder<?>> getSortsWithTieBreak() {
    final List<SortBuilder<?>> sorts = new ArrayList<>(getSorts());
    sorts.add(SortBuilders.fieldSort(INDEX_FIELD).order(SortOrder.ASC));
    sorts.add(SortBuilders.fieldSort(CODE_FIELD).order(SortOrder.ASC));
    return sorts.stream().distinct().collect(Collectors.toList());
  }
}
