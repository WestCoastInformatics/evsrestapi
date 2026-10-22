package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;

/**
 * A node in an EVS search query tree. Atomic queries match a single field with a single strategy;
 * composite queries combine other {@link EvsQuery} nodes.
 *
 * <p>Building a node's query may run searches first. A node can hold a child query about a
 * different concept, e.g. a role's target. One OpenSearch query only looks at one document, so the
 * node runs the child first with {@link #fetchMatchingCodes} and matches the codes it found.
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

  /** Codes fetched per page by {@link #fetchMatchingCodes}, at OpenSearch's default page limit. */
  static final int CODES_PER_PAGE = 10_000;

  /** Most codes one {@code terms} query accepts: OpenSearch's default index.max_terms_count. */
  static final int MAX_MATCHING_CODES = 65_536;

  /** The page every code page starts from. With search_after, paging is by sort values instead. */
  private static final int FIRST_PAGE = 0;

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
   * Returns the OpenSearch query for this node. Nodes holding a query about a different concept run
   * it first (see {@link #fetchMatchingCodes}).
   *
   * <p>Most nodes ignore {@code operations}. It's a parameter, rather than something nodes look up
   * themselves, so tests can pass a mock:
   *
   * <ul>
   *   <li>A static lookup (e.g. {@code StaticContextAccessor.getBean}) hides which nodes search,
   *       and mocking it changes global state shared by every test, or needs the whole Spring
   *       context.
   *   <li>Injecting it at construction (e.g. through a factory bean) means every tree containing a
   *       search needs {@code operations} just to be built. Taking it here keeps trees plain data
   *       that any code can build.
   * </ul>
   *
   * <p>For example, a unit test can check a role target's query without OpenSearch or Spring:
   *
   * <pre>{@code
   * final OpenSearchOperations operations = mock(OpenSearchOperations.class);
   * // Stub search(...) to return a page holding the codes the target should match
   * when(operations.search(any(), eq(Concept.class), any())).thenReturn(skinAndLungHits);
   *
   * final HasRole query =
   *     new HasRole(terminologies, new RoleType("R101"), new RoleTarget(skinOrLungQuery));
   * final QueryBuilder builder = query.fetchOpenSearchQuery(operations);
   * // builder now matches role entries whose target is Skin or Lung
   * }</pre>
   *
   * @param operations the OpenSearch operations, for any searches the node needs to run first
   * @return the query builder
   */
  public abstract QueryBuilder fetchOpenSearchQuery(OpenSearchOperations operations);

  /**
   * Returns the concept indexes of the terminologies, to search.
   *
   * @return the index coordinates
   */
  public IndexCoordinates getIndexCoordinates() {
    return IndexCoordinates.of(
        terminologies.stream().map(Terminology::getIndexName).toArray(String[]::new));
  }

  /**
   * Runs this query and returns the codes of every concept it matches, a page at a time. Used by
   * nodes whose child query describes a different concept, e.g. a role's target.
   *
   * @param operations the OpenSearch operations
   * @return the matching codes
   * @throws IllegalStateException if more concepts match than one {@code terms} query accepts
   */
  Set<String> fetchMatchingCodes(final OpenSearchOperations operations) {
    final QueryBuilder queryBuilder = fetchOpenSearchQuery(operations);
    final Set<String> codes = new HashSet<>();
    List<Object> searchAfter = null;
    boolean hasMorePages = true;
    while (hasMorePages) {
      final SearchHits<Concept> page =
          operations.search(
              getCodePageQuery(queryBuilder, searchAfter), Concept.class, getIndexCoordinates());
      codes.addAll(getCodes(page));
      checkMatchingCodeCount(codes.size());
      hasMorePages = page.getSearchHits().size() == CODES_PER_PAGE;
      searchAfter = hasMorePages ? getLastSortValues(page) : null;
    }
    return codes;
  }

  /**
   * Builds the search for one page of matching codes, ordered by code so pages don't overlap.
   *
   * @param queryBuilder the query
   * @param searchAfter the sort values of the previous page's last hit, or null for the first page
   * @return the search
   */
  private static NativeSearchQuery getCodePageQuery(
      final QueryBuilder queryBuilder, final List<Object> searchAfter) {
    final NativeSearchQuery search =
        new NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withSorts(
                SortBuilders.fieldSort(CODE_FIELD).order(SortOrder.ASC),
                SortBuilders.fieldSort(INDEX_FIELD).order(SortOrder.ASC))
            .withSourceFilter(new FetchSourceFilter(true, new String[] {CODE_FIELD}, null))
            .withPageable(PageRequest.of(FIRST_PAGE, CODES_PER_PAGE))
            .build();
    search.setSearchAfter(searchAfter);
    return search;
  }

  /**
   * Returns the codes of the concepts on a page.
   *
   * @param page the page
   * @return the codes
   */
  private static Set<String> getCodes(final SearchHits<Concept> page) {
    return page.stream()
        .map(SearchHit::getContent)
        .map(Concept::getCode)
        .collect(Collectors.toSet());
  }

  /**
   * Returns the sort values of a page's last hit, to start the next page after it.
   *
   * @param page a non-empty page
   * @return the sort values
   */
  private static List<Object> getLastSortValues(final SearchHits<Concept> page) {
    final List<SearchHit<Concept>> hits = page.getSearchHits();
    return hits.get(hits.size() - 1).getSortValues();
  }

  /**
   * Fails if more concepts matched than one {@code terms} query accepts, rather than cutting the
   * codes short and silently giving wrong results.
   *
   * @param codeCount the number of codes collected so far
   * @throws IllegalStateException if there are too many
   */
  private static void checkMatchingCodeCount(final int codeCount) {
    final boolean hasTooManyCodes = codeCount > MAX_MATCHING_CODES;
    if (hasTooManyCodes) {
      throw new IllegalStateException(
          "A query about another concept matched more than "
              + MAX_MATCHING_CODES
              + " concepts, more than one OpenSearch terms query accepts. Narrow that query.");
    }
  }
}
