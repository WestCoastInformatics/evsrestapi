package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.query.AtomicQueryTest.IndexedName;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.search.sort.SortBuilders;
import org.opensearch.search.sort.SortOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A tutorial for the logical queries: {@link And}, {@link Or}, {@link Not}, and the {@link Boost}
 * wrapper. They combine atomic queries (see {@link AtomicQueryTest}) into one OpenSearch query.
 *
 * <p>Every lesson runs against a small index of names, stored in a keyword field like the atomic
 * tutorial's, and checks which names came back, or in what order.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class LogicalQueryTest {

  /** A throwaway index used only by this tutorial. */
  private static final IndexCoordinates INDEX = IndexCoordinates.of("evs_logical_query_tutorial");

  /** One page large enough to hold every indexed name. */
  private static final Pageable ALL_NAMES = PageRequest.of(0, 100);

  /** The keyword field every lesson searches. */
  private static final String NAME_FIELD = "name";

  /** Logical queries don't filter by terminology. */
  private static final List<Terminology> TERMINOLOGIES = List.of();

  /** The names in the index. */
  private static final List<String> INDEXED_NAMES =
      List.of("Melanoma", "melanoma", "Malignant Melanoma", "Melanocyte", "Carcinoma", "NEVUS");

  /** The project's OpenSearch wrapper, which selects the correct operations bean. */
  @Autowired private OpensearchOperationsService operationsService;

  /** The OpenSearch operations, taken from {@link #operationsService}. */
  private OpenSearchOperations operations;

  /** Creates the index and loads {@link #INDEXED_NAMES}. */
  @BeforeAll
  public void setUpIndex() {
    operations = operationsService.getOpenSearchOperations();
    final IndexOperations indexOperations = operations.indexOps(INDEX);
    indexOperations.delete();
    indexOperations.create();
    indexOperations.putMapping(indexOperations.createMapping(IndexedName.class));

    final List<IndexedName> documents =
        INDEXED_NAMES.stream().map(IndexedName::new).collect(Collectors.toList());
    operations.save(documents, INDEX);
    indexOperations.refresh();
  }

  /** Removes the index. */
  @AfterAll
  public void tearDownIndex() {
    operations.indexOps(INDEX).delete();
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 1: And finds what every child finds.
  // ---------------------------------------------------------------------------------------------

  /** Starts with "Mel" and contains "oma": "Melanocyte" starts with "Mel" but has no "oma". */
  @Test
  public void testAndNeedsEveryChild() {
    final EvsQuery query =
        new And(
            TERMINOLOGIES,
            List.of(
                new StartsWith(TERMINOLOGIES, NAME_FIELD, "Mel"),
                new Contains(TERMINOLOGIES, NAME_FIELD, "oma")));

    assertEquals(Set.of("Melanoma"), fetchMatchingNames(query));
  }

  /** With no children there's nothing to fail, so everything matches. */
  @Test
  public void testAndWithoutChildrenMatchesEverything() {
    final EvsQuery query = new And(TERMINOLOGIES, List.of());

    assertEquals(Set.copyOf(INDEXED_NAMES), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: Or finds what any child finds.
  // ---------------------------------------------------------------------------------------------

  /** Either exact name matches. */
  @Test
  public void testOrNeedsAnyChild() {
    final EvsQuery query =
        new Or(
            TERMINOLOGIES,
            List.of(
                new Match(TERMINOLOGIES, NAME_FIELD, "Carcinoma"),
                new Match(TERMINOLOGIES, NAME_FIELD, "NEVUS")));

    assertEquals(Set.of("Carcinoma", "NEVUS"), fetchMatchingNames(query));
  }

  /**
   * With no children there are no alternatives, so nothing matches. A list of alternatives that
   * came back empty must not widen a search to everything.
   */
  @Test
  public void testOrWithoutChildrenMatchesNothing() {
    final EvsQuery query = new Or(TERMINOLOGIES, List.of());

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: Not finds what its child doesn't.
  // ---------------------------------------------------------------------------------------------

  /** Every name without "elan" in it. Matching is case sensitive, so "melanoma" has it too. */
  @Test
  public void testNotFindsWhatChildDoesNot() {
    final EvsQuery query = new Not(TERMINOLOGIES, new Contains(TERMINOLOGIES, NAME_FIELD, "elan"));

    assertEquals(Set.of("Carcinoma", "NEVUS"), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: Boost gives every match a fixed score, so boosted alternatives rank in tiers.
  // ---------------------------------------------------------------------------------------------

  /** The higher-boosted alternative ranks first, then the rest by name. */
  @Test
  public void testBoostRanksAlternativesInTiers() {
    final EvsQuery query = getCarcinomaThenMelanoQuery();

    assertEquals(List.of("Carcinoma", "Melanocyte", "Melanoma"), fetchRankedNames(query));
  }

  /** Not adds nothing to the score, so requiring it alongside the tiers leaves the order alone. */
  @Test
  public void testNotLeavesRankingUnchanged() {
    final EvsQuery query =
        new And(
            TERMINOLOGIES,
            List.of(
                new Not(TERMINOLOGIES, new Match(TERMINOLOGIES, NAME_FIELD, "NEVUS")),
                getCarcinomaThenMelanoQuery()));

    assertEquals(List.of("Carcinoma", "Melanocyte", "Melanoma"), fetchRankedNames(query));
  }

  /**
   * Builds a query for "Carcinoma" (score 2) or names starting with "Melano" (score 1).
   *
   * @return the query
   */
  private EvsQuery getCarcinomaThenMelanoQuery() {
    final float firstTierScore = 2.0f;
    final float secondTierScore = 1.0f;
    return new Or(
        TERMINOLOGIES,
        List.of(
            new Boost(
                TERMINOLOGIES, new Match(TERMINOLOGIES, NAME_FIELD, "Carcinoma"), firstTierScore),
            new Boost(
                TERMINOLOGIES,
                new StartsWith(TERMINOLOGIES, NAME_FIELD, "Melano"),
                secondTierScore)));
  }

  /**
   * Runs the query against the tutorial index.
   *
   * @param query the query to run
   * @return the names that matched
   */
  private Set<String> fetchMatchingNames(final EvsQuery query) {
    final NativeSearchQuery search =
        new NativeSearchQueryBuilder()
            .withQuery(query.fetchOpenSearchQuery(operations))
            .withPageable(ALL_NAMES)
            .build();
    return operations.search(search, IndexedName.class, INDEX).stream()
        .map(SearchHit::getContent)
        .map(IndexedName::getName)
        .collect(Collectors.toSet());
  }

  /**
   * Runs the query against the tutorial index, best score first, then by name.
   *
   * @param query the query to run
   * @return the names that matched, in rank order
   */
  private List<String> fetchRankedNames(final EvsQuery query) {
    final NativeSearchQuery search =
        new NativeSearchQueryBuilder()
            .withQuery(query.fetchOpenSearchQuery(operations))
            .withSorts(
                SortBuilders.scoreSort().order(SortOrder.DESC),
                SortBuilders.fieldSort(NAME_FIELD).order(SortOrder.ASC))
            .withPageable(ALL_NAMES)
            .build();
    return operations.search(search, IndexedName.class, INDEX).stream()
        .map(SearchHit::getContent)
        .map(IndexedName::getName)
        .collect(Collectors.toList());
  }
}
