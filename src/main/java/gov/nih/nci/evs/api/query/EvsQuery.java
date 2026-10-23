package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Terminology;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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

/**
 * A node in an EVS search query tree. Atomic queries match a single field with a single strategy;
 * composite queries combine other {@link EvsQuery} nodes.
 *
 * <p>Building a node's query may run searches first. A node can depend on other concepts, e.g. a
 * role's target. One OpenSearch query only looks at one document, so the node runs the other query
 * first with {@link #declareDependencies} and matches the concepts it found.
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

  /**
   * Concepts fetched per page by {@link #declareDependencies}, at OpenSearch's default page limit.
   */
  static final int CONCEPTS_PER_PAGE = 10_000;

  /**
   * The page number every concept page asks for. The page cursor, not the page number, moves
   * through the pages.
   */
  private static final int FIRST_PAGE = 0;

  /**
   * Stored fields value that fetches none, not even {@code _source} or {@code _id}, so hits are
   * identified from their sort values and index without reading any stored document. For
   * efficiency: see {@link #fetchConceptKeys}.
   */
  static final String NO_STORED_FIELDS = "_none_";

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
   * Returns the OpenSearch query for this node. A node that depends on other concepts runs their
   * queries first (see {@link #declareDependencies}), which is why this may go to OpenSearch.
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
   * Runs each dependency query and returns every concept it matches, in the same order as the
   * dependencies. For a node whose query depends on other concepts, e.g. a role's target, called
   * from its {@link #fetchOpenSearchQuery}.
   *
   * @param operations the OpenSearch operations
   * @param dependencies the queries this node depends on
   * @return the concepts each dependency matched
   */
  protected static List<ConceptKeys> declareDependencies(
      final OpenSearchOperations operations, final EvsQuery... dependencies) {
    return Arrays.stream(dependencies)
        .map(dependency -> dependency.fetchConceptKeys(operations))
        .collect(Collectors.toList());
  }

  /**
   * Runs this node's query ({@link #fetchOpenSearchQuery}) and returns the key of every concept it
   * matches, a page at a time. Called by {@link #declareDependencies}. The query is whatever the
   * node's own {@link #fetchOpenSearchQuery} builds, so a node is written once and works both as a
   * search and as a dependency.
   *
   * <p><b>Why the matches are returned without their documents.</b> The node's query decides which
   * concepts match; the search request wrapped around it decides what comes back for each match.
   * Only the request is special here: the query is used as is. A normal search returns each hit's
   * stored document ({@code _source}), which Spring maps to a {@link Concept}. A dependency doesn't
   * need any of that: only which codes matched, in which terminology version. Reading documents
   * would be pure cost, and a large one. Concept documents can be very large (C3262's is about 1.4
   * MB, with its paths and descendants), and OpenSearch loads and decompresses a whole document
   * even to return a few of its fields, or only its {@code _id}. Filtering {@code _source} to the
   * code, terminology, and version took about 40 seconds for the 15,233 concepts below C3262 in
   * NCIt.
   *
   * <p>So this search reads no stored fields at all ({@link #NO_STORED_FIELDS}), and each hit is
   * identified without its document. That takes under a second for the same 15,233 concepts.
   *
   * <ul>
   *   <li>The <b>code</b> is the hit's first sort value. The pages are sorted by code anyway, and
   *       OpenSearch reads sort values from the code's doc values, a per-field column, not the
   *       document.
   *   <li>The <b>terminology and version</b> come from the index the hit is in. Each terminology
   *       version has its own index, and this node's {@link #terminologies} say which terminology
   *       version each searched index holds.
   * </ul>
   *
   * <p>The hits' {@link Concept} content is empty and isn't read.
   *
   * @param operations the OpenSearch operations
   * @return the keys of the matching concepts
   */
  protected ConceptKeys fetchConceptKeys(final OpenSearchOperations operations) {
    final QueryBuilder queryBuilder = fetchOpenSearchQuery(operations);

    final Map<ConceptKeys.TerminologyVersion, Set<String>> codesByTerminologyVersion =
        new HashMap<>();

    fetchAndAddCodesFromAllPages(operations, queryBuilder, codesByTerminologyVersion);

    return ConceptKeys.of(codesByTerminologyVersion);
  }

  /**
   * Fetches every page of the query's matches, adding each hit's code under its terminology
   * version.
   *
   * @param operations the OpenSearch operations
   * @param queryToFetchBuilder the query to fetch the matches of
   * @param codesByTerminologyVersion the codes found so far, added to
   */
  private void fetchAndAddCodesFromAllPages(
      final OpenSearchOperations operations,
      final QueryBuilder queryToFetchBuilder,
      final Map<ConceptKeys.TerminologyVersion, Set<String>> codesByTerminologyVersion) {
    final Map<String, ConceptKeys.TerminologyVersion> terminologyVersionsByIndex =
        getTerminologyVersionsByIndex();
    // List<Object> is the shape of the page cursor that is relevant to OpenSearch
    // what is actually in it is largely irrelevant to us beyond the fact that OpenSearch
    // can use it as a pageCursor
    List<Object> pageCursor = null;

    boolean hasMorePages = true;
    while (hasMorePages) {
      final SearchHits<Concept> page = fetchKeyPage(operations, queryToFetchBuilder, pageCursor);

      addPageCodes(codesByTerminologyVersion, terminologyVersionsByIndex, page);

      // should continue leafing through pages and recording their concepts?
      hasMorePages = page.getSearchHits().size() == CONCEPTS_PER_PAGE;
      pageCursor = hasMorePages ? getNextPageCursor(page) : null;
    }
  }

  /**
   * Fetches one page of the query's matches as keys: each hit has its index and sort values, and no
   * document. The hits are typed {@link Concept} because Spring maps every hit to an entity class,
   * but that content is empty.
   *
   * @param operations the OpenSearch operations
   * @param queryToFetchBuilder the query to fetch the matches of
   * @param pageCursor where the page starts, or null for the first page
   * @return the page
   */
  private SearchHits<Concept> fetchKeyPage(
      final OpenSearchOperations operations,
      final QueryBuilder queryToFetchBuilder,
      final List<Object> pageCursor) {
    return operations.search(
        getConceptKeyPageQuery(queryToFetchBuilder, pageCursor),
        Concept.class,
        getIndexCoordinates());
  }

  /**
   * Adds each hit's code, its first sort value, under the terminology version of the index it's in.
   *
   * @param codesByTerminologyVersion the codes found so far, added to
   * @param terminologyVersionsByIndex the terminology version of each searched index
   * @param page the page
   */
  private static void addPageCodes(
      final Map<ConceptKeys.TerminologyVersion, Set<String>> codesByTerminologyVersion,
      final Map<String, ConceptKeys.TerminologyVersion> terminologyVersionsByIndex,
      final SearchHits<Concept> page) {
    for (final SearchHit<Concept> hit : page.getSearchHits()) {
      final ConceptKeys.TerminologyVersion terminologyVersion =
          terminologyVersionsByIndex.get(hit.getIndex());
      // The search only covers this node's indexes, so every hit should be in one of them. If not
      // (e.g. a terminology's indexName is an alias, and hits name the real index), the hit's
      // terminology version is unknown: fail rather than drop or mislabel its code
      if (terminologyVersion == null) {
        throw new IllegalStateException(
            "Hit from index " + hit.getIndex() + ", which isn't a searched terminology's index");
      }
      final String code = (String) hit.getSortValues().get(0);
      codesByTerminologyVersion.computeIfAbsent(terminologyVersion, k -> new HashSet<>()).add(code);
    }
  }

  /**
   * Returns the terminology version of each searched index. Each terminology version has its own
   * index.
   *
   * @return the terminology versions by index name
   */
  private Map<String, ConceptKeys.TerminologyVersion> getTerminologyVersionsByIndex() {
    final Map<String, ConceptKeys.TerminologyVersion> terminologyVersionsByIndex = new HashMap<>();
    for (final Terminology terminology : terminologies) {
      terminologyVersionsByIndex.put(
          terminology.getIndexName(),
          new ConceptKeys.TerminologyVersion(
              terminology.getTerminology(), terminology.getVersion()));
    }
    return terminologyVersionsByIndex;
  }

  /**
   * Builds the search for one page of the keys of matching concepts, ordered by code so pages don't
   * overlap. It returns no stored fields, only each hit's index and sort values, which identify it
   * (see {@link #fetchConceptKeys}).
   *
   * @param queryBuilder the query
   * @param pageCursor where the page starts: the sort values of the previous page's last hit, or
   *     null for the first page
   * @return the search
   */
  private static NativeSearchQuery getConceptKeyPageQuery(
      final QueryBuilder queryBuilder, final List<Object> pageCursor) {
    final NativeSearchQuery search =
        new NativeSearchQueryBuilder()
            .withQuery(queryBuilder)
            .withSorts(
                SortBuilders.fieldSort(CODE_FIELD).order(SortOrder.ASC),
                SortBuilders.fieldSort(INDEX_FIELD).order(SortOrder.ASC))
            .withStoredFields(NO_STORED_FIELDS)
            .withPageable(PageRequest.of(FIRST_PAGE, CONCEPTS_PER_PAGE))
            .build();
    search.setSearchAfter(pageCursor);
    return search;
  }

  /**
   * Returns the cursor that starts the next page: the sort values of a page's last hit.
   *
   * @param page a non-empty page
   * @return the cursor
   */
  private static List<Object> getNextPageCursor(final SearchHits<Concept> page) {
    final List<SearchHit<Concept>> hits = page.getSearchHits();
    return hits.get(hits.size() - 1).getSortValues();
  }
}
