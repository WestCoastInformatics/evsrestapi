package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Terminology;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.core.OpenSearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

/**
 * Unit tests for {@link EvsQuery#fetchMatchingConcepts}, which runs a query about another concept
 * (e.g. a role target) and returns every match, a page at a time.
 *
 * <p>No OpenSearch or Spring: a mocked {@link OpenSearchOperations} returns pages of made-up
 * concepts. That makes it cheap to return more than one page, which would otherwise take tens of
 * thousands of indexed documents.
 */
public class FetchMatchingConceptsTest {

  /** Index name the mocked pages claim their hits came from. */
  private static final String INDEX_NAME = "concept_test";

  /** Terminology of the made-up concepts. */
  private static final String TERMINOLOGY = "ncit";

  /** Version of the made-up concepts. */
  private static final String VERSION = "25.12e";

  /** Hits on a page that isn't full, so it's the last page. */
  private static final int PARTIAL_PAGE_SIZE = 3;

  /** The terminology searched. */
  private List<Terminology> terminologies;

  /** The query whose matching concepts are fetched. The mock ignores what it matches. */
  private EvsQuery query;

  /** Mocked OpenSearch operations, stubbed per test. */
  private OpenSearchOperations operations;

  /** Builds the query and a fresh mock. */
  @BeforeEach
  public void setUp() {
    final Terminology terminology = new Terminology();
    terminology.setIndexName(INDEX_NAME);
    terminologies = List.of(terminology);
    query = new Match(terminologies, "code", "C12470");
    operations = mock(OpenSearchOperations.class);
  }

  /** One page that isn't full is the only page: one search, all of its concepts. */
  @Test
  public void testPartialPageIsTheLastPage() {
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(getPage(0, PARTIAL_PAGE_SIZE));

    final List<Concept> concepts = query.fetchMatchingConcepts(operations);

    assertEquals(PARTIAL_PAGE_SIZE, concepts.size());
    captureSearches(1);
  }

  /**
   * Only the fields that identify a concept are fetched: its code, terminology, and version. The
   * terminology and version say which version of the terminology a code belongs to.
   */
  @Test
  public void testFetchesCodeTerminologyAndVersion() {
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(getPage(0, PARTIAL_PAGE_SIZE));

    query.fetchMatchingConcepts(operations);

    final NativeSearchQuery search = (NativeSearchQuery) captureSearches(1).get(0);
    assertEquals(
        Set.of("code", "terminology", "version"), Set.of(search.getSourceFilter().getIncludes()));
  }

  /**
   * A full page means there may be more, so the next page is fetched starting after the last hit's
   * sort values. Concepts from every page are returned.
   */
  @Test
  public void testFullPageFetchesTheNextPageAfterItsLastHit() {
    final SearchHits<Concept> fullPage = getPage(0, EvsQuery.CONCEPTS_PER_PAGE);
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(fullPage, getPage(EvsQuery.CONCEPTS_PER_PAGE, PARTIAL_PAGE_SIZE));

    final List<Concept> concepts = query.fetchMatchingConcepts(operations);

    assertEquals(EvsQuery.CONCEPTS_PER_PAGE + PARTIAL_PAGE_SIZE, concepts.size());
    final List<Query> searches = captureSearches(2);
    assertNull(searches.get(0).getSearchAfter());
    final List<SearchHit<Concept>> firstPageHits = fullPage.getSearchHits();
    final List<Object> lastHitSortValues =
        firstPageHits.get(firstPageHits.size() - 1).getSortValues();
    assertEquals(lastHitSortValues, searches.get(1).getSearchAfter());
  }

  /**
   * A last page of exactly {@link EvsQuery#CONCEPTS_PER_PAGE} hits looks like there may be more, so
   * one more search runs and comes back empty.
   */
  @Test
  public void testExactlyFullLastPageEndsOnAnEmptyPage() {
    final int noHits = 0;
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(
            getPage(0, EvsQuery.CONCEPTS_PER_PAGE), getPage(EvsQuery.CONCEPTS_PER_PAGE, noHits));

    final List<Concept> concepts = query.fetchMatchingConcepts(operations);

    assertEquals(EvsQuery.CONCEPTS_PER_PAGE, concepts.size());
    captureSearches(2);
  }

  /**
   * Verifies the number of searches run and returns their queries, in order.
   *
   * @param expectedSearches how many searches should have run
   * @return the queries searched
   */
  private List<Query> captureSearches(final int expectedSearches) {
    final ArgumentCaptor<Query> searches = ArgumentCaptor.forClass(Query.class);
    verify(operations, times(expectedSearches))
        .search(searches.capture(), eq(Concept.class), any(IndexCoordinates.class));
    return searches.getAllValues();
  }

  /**
   * Builds a page of made-up concepts with consecutive codes, each hit sorted by code then index
   * like the real concept-page search.
   *
   * @param firstCodeNumber the number in the first concept's code
   * @param hitCount how many hits the page holds
   * @return the page
   */
  private static SearchHits<Concept> getPage(final int firstCodeNumber, final int hitCount) {
    final List<SearchHit<Concept>> hits = new ArrayList<>();
    for (int codeNumber = firstCodeNumber; codeNumber < firstCodeNumber + hitCount; codeNumber++) {
      hits.add(getHit("C" + codeNumber));
    }
    final float maxScore = 0f;
    return new SearchHitsImpl<>(
        hits.size(),
        TotalHitsRelation.EQUAL_TO,
        maxScore,
        Duration.ZERO,
        null,
        null,
        hits,
        null,
        null,
        null);
  }

  /**
   * Builds one hit for a concept with the given code.
   *
   * @param code the concept code
   * @return the hit
   */
  private static SearchHit<Concept> getHit(final String code) {
    final Concept concept = new Concept();
    concept.setCode(code);
    concept.setTerminology(TERMINOLOGY);
    concept.setVersion(VERSION);
    final float score = 0f;
    final Object[] sortValues = {code, INDEX_NAME};
    return new SearchHit<>(
        INDEX_NAME,
        code,
        null,
        score,
        sortValues,
        Map.of(),
        Map.of(),
        null,
        null,
        List.of(),
        concept);
  }
}
