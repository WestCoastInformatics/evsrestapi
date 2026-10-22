package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.data.core.OpenSearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

/**
 * Unit tests for {@link EvsQuery#fetchMatchingCodes}, which runs a query about another concept
 * (e.g. a role target) and collects the codes of every match, a page at a time.
 *
 * <p>No OpenSearch or Spring: a mocked {@link OpenSearchOperations} returns pages of made-up
 * concepts. That makes it cheap to return more than one page, or more codes than one {@code terms}
 * query accepts, which would otherwise take tens of thousands of indexed documents.
 */
public class FetchMatchingCodesTest {

  /** Index name the mocked pages claim their hits came from. */
  private static final String INDEX_NAME = "concept_test";

  /** Hits on a page that isn't full, so it's the last page. */
  private static final int PARTIAL_PAGE_SIZE = 3;

  /** The query whose matching codes are fetched. The mock ignores what it matches. */
  private EvsQuery query;

  /** Mocked OpenSearch operations, stubbed per test. */
  private OpenSearchOperations operations;

  /** Builds the query and a fresh mock. */
  @BeforeEach
  public void setUp() {
    final Terminology terminology = new Terminology();
    terminology.setIndexName(INDEX_NAME);
    query = new Match(List.of(terminology), "code", "C12470");
    operations = mock(OpenSearchOperations.class);
  }

  /** One page that isn't full is the only page: one search, all of its codes. */
  @Test
  public void testPartialPageIsTheLastPage() {
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(getPage(0, PARTIAL_PAGE_SIZE));

    final Set<String> codes = query.fetchMatchingCodes(operations);

    assertEquals(PARTIAL_PAGE_SIZE, codes.size());
    final int expectedSearches = 1;
    verify(operations, times(expectedSearches))
        .search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class));
  }

  /**
   * A full page means there may be more, so the next page is fetched starting after the last hit's
   * sort values. Codes from every page are collected.
   */
  @Test
  public void testFullPageFetchesTheNextPageAfterItsLastHit() {
    final SearchHits<Concept> fullPage = getPage(0, EvsQuery.CODES_PER_PAGE);
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(fullPage, getPage(EvsQuery.CODES_PER_PAGE, PARTIAL_PAGE_SIZE));

    final Set<String> codes = query.fetchMatchingCodes(operations);

    assertEquals(EvsQuery.CODES_PER_PAGE + PARTIAL_PAGE_SIZE, codes.size());
    final List<Query> searches = captureSearches(2);
    assertNull(searches.get(0).getSearchAfter());
    final List<SearchHit<Concept>> firstPageHits = fullPage.getSearchHits();
    final List<Object> lastHitSortValues =
        firstPageHits.get(firstPageHits.size() - 1).getSortValues();
    assertEquals(lastHitSortValues, searches.get(1).getSearchAfter());
  }

  /**
   * A last page of exactly {@link EvsQuery#CODES_PER_PAGE} hits looks like there may be more, so
   * one more search runs and comes back empty.
   */
  @Test
  public void testExactlyFullLastPageEndsOnAnEmptyPage() {
    final int noHits = 0;
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenReturn(getPage(0, EvsQuery.CODES_PER_PAGE), getPage(EvsQuery.CODES_PER_PAGE, noHits));

    final Set<String> codes = query.fetchMatchingCodes(operations);

    assertEquals(EvsQuery.CODES_PER_PAGE, codes.size());
    captureSearches(2);
  }

  /**
   * More matches than one {@code terms} query accepts fails, rather than cutting the codes short.
   * It fails on the first page that crosses the limit, without fetching the rest.
   */
  @Test
  public void testMoreCodesThanOneTermsQueryAcceptsFails() {
    final AtomicInteger pagesReturned = new AtomicInteger();
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenAnswer(
            invocation ->
                getPage(
                    pagesReturned.getAndIncrement() * EvsQuery.CODES_PER_PAGE,
                    EvsQuery.CODES_PER_PAGE));

    assertThrows(IllegalStateException.class, () -> query.fetchMatchingCodes(operations));

    final int pagesToCrossLimit = EvsQuery.MAX_MATCHING_CODES / EvsQuery.CODES_PER_PAGE + 1;
    assertEquals(pagesToCrossLimit, pagesReturned.get());
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
   * like the real code-page search.
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
