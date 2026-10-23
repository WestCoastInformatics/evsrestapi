package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.ConceptMinimal;
import gov.nih.nci.evs.api.model.Path;
import gov.nih.nci.evs.api.model.Paths;
import gov.nih.nci.evs.api.model.Terminology;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.IdsQueryBuilder;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.SearchHitsImpl;
import org.springframework.data.elasticsearch.core.TotalHitsRelation;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Query;

/**
 * Unit tests for the limits and batching of the hierarchy query nodes: sets too large for one
 * {@code terms} query are rejected with a clear message, missing arguments fail fast, and lookups
 * of many anchors are split into batches.
 *
 * <p>No OpenSearch or Spring: a mocked {@link OpenSearchOperations} returns made-up concepts. That
 * makes it cheap to return tens of thousands of descendants or ancestors, which {@link
 * HierarchyQueryTest} can't index.
 */
public class HierarchyQueryLimitsTest {

  /** Index name of the made-up terminology. */
  private static final String INDEX_NAME = "concept_limits_test";

  /** Terminology of the made-up concepts. */
  private static final String TERMINOLOGY = "ncit";

  /** Version of the made-up concepts. */
  private static final String VERSION = "1";

  /** One more than fits in a {@code terms} query. */
  private static final int TOO_MANY = MatchAnyOf.MAX_VALUES + 1;

  /** Anchors in the batching tests: two full lookups and one partial. */
  private static final int MANY_ANCESTORS = 2 * StoredHierarchyFallback.CONCEPTS_PER_LOOKUP + 500;

  /** A terminology indexed without ancestor fields, so {@link DescendantOf} uses the fallback. */
  private Terminology terminology;

  /** The terminology searched, as a list. */
  private List<Terminology> terminologies;

  /** Mocked OpenSearch operations, stubbed per test. */
  private OpenSearchOperations operations;

  /** Builds the terminology and a fresh mock. */
  @BeforeEach
  public void setUp() {
    terminology = new Terminology();
    terminology.setTerminology(TERMINOLOGY);
    terminology.setVersion(VERSION);
    terminology.setIndexName(INDEX_NAME);
    terminologies = List.of(terminology);
    operations = mock(OpenSearchOperations.class);
  }

  /** MatchAnyOf accepts exactly the limit and rejects one more, naming the field and count. */
  @Test
  public void testMatchAnyOfRejectsMoreThanTheLimit() {
    assertDoesNotThrow(
        () -> new MatchAnyOf(terminologies, "ancestors", codes(MatchAnyOf.MAX_VALUES)));

    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchAnyOf(terminologies, "ancestors", codes(TOO_MANY)));
    assertTrue(e.getMessage().contains("'ancestors'"), e.getMessage());
    assertTrue(e.getMessage().contains(String.valueOf(TOO_MANY)), e.getMessage());
  }

  /** Depth tokens are anchors times maxLevel; more than the limit is rejected. */
  @Test
  public void testDepthTokensRejectsMoreThanTheLimit() {
    assertEquals(List.of("C1|1", "C1|2"), DescendantOf.depthTokens(List.of("C1"), 2), "one anchor");
    assertEquals(
        MatchAnyOf.MAX_VALUES,
        DescendantOf.depthTokens(codes(MatchAnyOf.MAX_VALUES / 4), 4).size(),
        "exactly the limit");

    assertThrows(IllegalArgumentException.class, () -> DescendantOf.depthTokens(codes(70_000), 1));
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class, () -> DescendantOf.depthTokens(codes(10_000), 7));
    assertTrue(e.getMessage().contains("maxLevel 7"), e.getMessage());
  }

  /** A missing anchor query or code fails when the node is built, naming the argument. */
  @Test
  public void testNullAnchorIsRejected() {
    assertEquals(
        "anchorQuery",
        assertThrows(
                NullPointerException.class, () -> new DescendantOf(terminologies, (EvsQuery) null))
            .getMessage());
    assertEquals(
        "anchorQuery",
        assertThrows(
                NullPointerException.class,
                () -> new AncestorOrSelfOf(terminologies, (EvsQuery) null, 1))
            .getMessage());
    assertEquals(
        "code",
        assertThrows(NullPointerException.class, () -> new AncestorOf(terminologies, (String) null))
            .getMessage());
    assertEquals(
        "code",
        assertThrows(
                NullPointerException.class,
                () -> new DescendantOrSelfOf(terminologies, (String) null, 2))
            .getMessage());
  }

  /**
   * On an index without ancestor fields, a branch with more descendants than fit in one query is
   * rejected, telling the caller to reindex.
   */
  @Test
  public void testFallbackRejectsTooManyDescendants() {
    stub(
        anchor -> {
          final Concept concept = concept(anchor);
          concept.setDescendants(codes(TOO_MANY).stream().map(Concept::new).toList());
          return concept;
        });

    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new DescendantOf(terminologies, "R").fetchOpenSearchQuery(operations));
    assertTrue(e.getMessage().contains("reindex the terminology"), e.getMessage());
  }

  /** The same limit applies to the fallback's depth-limited walk over children. */
  @Test
  public void testFallbackWithMaxLevelRejectsTooManyDescendants() {
    stub(
        anchor -> {
          final Concept concept = concept(anchor);
          concept.setChildren(codes(TOO_MANY).stream().map(Concept::new).toList());
          return concept;
        });

    assertThrows(
        IllegalArgumentException.class,
        () -> new DescendantOf(terminologies, "R", 1).fetchOpenSearchQuery(operations));
  }

  /** An anchor with more ancestors than fit in one query is rejected. */
  @Test
  public void testAncestorOfRejectsTooManyAncestors() {
    stub(
        anchor -> {
          final List<ConceptMinimal> steps = new ArrayList<>();
          steps.add(step(anchor, 0));
          int level = 1;
          for (final String code : codes(TOO_MANY)) {
            steps.add(step(code, level++));
          }
          final Concept concept = concept(anchor);
          concept.setPaths(new Paths(List.of(new Path(1, steps))));
          return concept;
        });

    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new AncestorOf(terminologies, "G").fetchOpenSearchQuery(operations));
    assertTrue(e.getMessage().contains("Too many ancestors"), e.getMessage());
  }

  /** The fallback reads many anchors' descendants in lookups of at most 1,000 concepts. */
  @Test
  public void testFallbackLooksUpAnchorsInBatches() {
    stub(
        anchor -> {
          final Concept concept = concept(anchor);
          concept.setDescendants(List.of(new Concept(anchor + "-child")));
          return concept;
        });

    final Set<String> found =
        StoredHierarchyFallback.fetchDescendantCodes(
            operations, terminology, codes(MANY_ANCESTORS), null);

    assertEquals(MANY_ANCESTORS, found.size());
    assertTrue(found.contains("C0-child") && found.contains("C2499-child"));
    assertEquals(List.of(1000, 1000, 500), lookupSizes(3));
  }

  /** Stored paths for many anchors are read in lookups of at most 1,000 concepts. */
  @Test
  public void testStoredPathsLooksUpAnchorsInBatches() {
    stub(
        anchor -> {
          final Concept concept = concept(anchor);
          concept.setPaths(
              new Paths(
                  List.of(new Path(1, List.of(step(anchor, 0), step(anchor + "-parent", 1))))));
          return concept;
        });

    final Set<String> found =
        StoredPaths.fetchAncestorCodes(operations, terminology, codes(MANY_ANCESTORS), null);

    assertEquals(MANY_ANCESTORS, found.size());
    assertTrue(found.contains("C0-parent") && found.contains("C2499-parent"));
    assertEquals(List.of(1000, 1000, 500), lookupSizes(3));
  }

  /**
   * Stubs the mock: a lookup by ids returns one concept per id, built by {@code byCode}; any other
   * search (the inner query, run through {@link EvsQuery#declareDependencies}) returns one page
   * with the matched anchor.
   *
   * @param byCode builds the stored concept for a code
   */
  private void stub(final Function<String, Concept> byCode) {
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenAnswer(
            invocation -> {
              final NativeSearchQuery search = invocation.getArgument(0);
              if (search.getQuery() instanceof IdsQueryBuilder ids) {
                return page(ids.ids().stream().map(byCode).toList());
              }
              // The inner query matched the anchor by code; the mock ignores which code
              return page(List.of(concept("anchor")));
            });
  }

  /**
   * Returns the number of ids in each lookup by ids, in order, after checking how many ran.
   *
   * @param expectedLookups how many lookups by ids should have run
   * @return the number of ids in each
   */
  private List<Integer> lookupSizes(final int expectedLookups) {
    final ArgumentCaptor<Query> searches = ArgumentCaptor.forClass(Query.class);
    verify(operations, times(expectedLookups))
        .search(searches.capture(), eq(Concept.class), any(IndexCoordinates.class));
    return searches.getAllValues().stream()
        .map(search -> ((IdsQueryBuilder) ((NativeSearchQuery) search).getQuery()).ids().size())
        .toList();
  }

  /**
   * Returns made-up codes C0, C1, and so on.
   *
   * @param count how many
   * @return the codes
   */
  private static Set<String> codes(final int count) {
    final Set<String> codes = new HashSet<>();
    for (int i = 0; i < count; i++) {
      codes.add("C" + i);
    }
    return codes;
  }

  /**
   * Builds a concept in the made-up terminology version.
   *
   * @param code the code
   * @return the concept
   */
  private static Concept concept(final String code) {
    final Concept concept = new Concept(code);
    concept.setTerminology(TERMINOLOGY);
    concept.setVersion(VERSION);
    return concept;
  }

  /**
   * Builds one step of a path to root.
   *
   * @param code the step's code
   * @param level its distance from the path's first concept
   * @return the step
   */
  private static ConceptMinimal step(final String code, final int level) {
    final ConceptMinimal step = new ConceptMinimal(code);
    step.setLevel(level);
    return step;
  }

  /**
   * Builds one page of hits holding the given concepts, sorted like the real concept-page search.
   *
   * @param concepts the concepts
   * @return the page
   */
  private static SearchHits<Concept> page(final Collection<Concept> concepts) {
    final List<SearchHit<Concept>> hits = new ArrayList<>();
    for (final Concept concept : concepts) {
      final Object[] sortValues = {concept.getCode(), INDEX_NAME};
      hits.add(
          new SearchHit<>(
              INDEX_NAME,
              concept.getCode(),
              null,
              0f,
              sortValues,
              Map.of(),
              Map.of(),
              null,
              null,
              List.of(),
              concept));
    }
    return new SearchHitsImpl<>(
        hits.size(),
        TotalHitsRelation.EQUAL_TO,
        0f,
        Duration.ZERO,
        null,
        null,
        hits,
        null,
        null,
        null);
  }
}
