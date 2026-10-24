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

  /** Field name used when building a {@link MatchAnyOf}. */
  private static final String ANCESTORS_FIELD = "ancestors";

  /** One more than fits in a {@code terms} query. */
  private static final int TOO_MANY = MatchAnyOf.MAX_VALUES + 1;

  /** A max level of one: direct children or parents. */
  private static final int ONE_LEVEL = 1;

  /** Code of the anchor in the descendant tests. */
  private static final String DESCENDANT_ANCHOR_CODE = "R";

  /** Code of the anchor in the ancestor tests. */
  private static final String ANCESTOR_ANCHOR_CODE = "G";

  /** Code of the concept the mocked inner query matches. */
  private static final String MATCHED_ANCHOR_CODE = "anchor";

  /** Code of the single anchor in the depth token tests. */
  private static final String DEPTH_ANCHOR_CODE = "C1";

  /** Max level in the one-anchor depth token test. */
  private static final int ONE_ANCHOR_MAX_LEVEL = 2;

  /** Max level whose anchors times levels exactly reach the limit. */
  private static final int LEVELS_AT_LIMIT = 4;

  /** Anchors that alone are over the limit, with a max level of one. */
  private static final int ANCHORS_OVER_LIMIT = 70_000;

  /** Anchors that are over the limit only once multiplied by {@link #LEVELS_OVER_LIMIT}. */
  private static final int ANCHORS_UNDER_LIMIT = 10_000;

  /** Max level that pushes {@link #ANCHORS_UNDER_LIMIT} anchors over the limit. */
  private static final int LEVELS_OVER_LIMIT = 7;

  /** The message fragment naming the max level of an over-limit set of depth tokens. */
  private static final String MAX_LEVEL_MESSAGE = "maxLevel " + LEVELS_OVER_LIMIT;

  /** The argument named when the anchor query is null. */
  private static final String ANCHOR_QUERY_ARGUMENT = "anchorQuery";

  /** The argument named when the code is null. */
  private static final String CODE_ARGUMENT = "code";

  /** Message fragment telling the caller to reindex. */
  private static final String REINDEX_MESSAGE = "reindex the terminology";

  /** Message fragment of the too-many-ancestors error. */
  private static final String TOO_MANY_ANCESTORS_MESSAGE = "Too many ancestors";

  /** Size of the last, partial lookup in the batching tests. */
  private static final int PARTIAL_LOOKUP_SIZE = 500;

  /** Anchors in the batching tests: two full lookups and one partial. */
  private static final int MANY_ANCHORS =
      2 * StoredHierarchyFallback.CONCEPTS_PER_LOOKUP + PARTIAL_LOOKUP_SIZE;

  /** Lookups expected for {@link #MANY_ANCHORS} anchors. */
  private static final int EXPECTED_LOOKUP_COUNT = 3;

  /** Ids per lookup expected for {@link #MANY_ANCHORS} anchors. */
  private static final List<Integer> EXPECTED_LOOKUP_SIZES =
      List.of(
          StoredHierarchyFallback.CONCEPTS_PER_LOOKUP,
          StoredHierarchyFallback.CONCEPTS_PER_LOOKUP,
          PARTIAL_LOOKUP_SIZE);

  /** Code of the first generated anchor. */
  private static final String FIRST_ANCHOR_CODE = "C0";

  /** Code of the last generated anchor. */
  private static final String LAST_ANCHOR_CODE = "C" + (MANY_ANCHORS - 1);

  /** Suffix of the made-up child of each anchor. */
  private static final String CHILD_SUFFIX = "-child";

  /** Suffix of the made-up parent of each anchor. */
  private static final String PARENT_SUFFIX = "-parent";

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

  /** MatchAnyOf with exactly the limit of values is accepted. */
  @Test
  public void testMatchAnyOfAtLimitIsAccepted() {
    assertDoesNotThrow(
        () -> new MatchAnyOf(terminologies, ANCESTORS_FIELD, getCodes(MatchAnyOf.MAX_VALUES)));
  }

  /** MatchAnyOf with one more value than the limit throws an error naming the field and count. */
  @Test
  public void testMatchAnyOfOverLimitThrowsErrorNamingFieldAndCount() {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> new MatchAnyOf(terminologies, ANCESTORS_FIELD, getCodes(TOO_MANY)));

    assertTrue(e.getMessage().contains("'" + ANCESTORS_FIELD + "'"), e.getMessage());
    assertTrue(e.getMessage().contains(String.valueOf(TOO_MANY)), e.getMessage());
  }

  /** One anchor gives one depth token per level up to the max level. */
  @Test
  public void testDepthTokensForOneAnchorGivesOneTokenPerDepth() {
    final List<String> expectedTokens =
        List.of(
            DEPTH_ANCHOR_CODE + DescendantOf.DEPTH_SEPARATOR + 1,
            DEPTH_ANCHOR_CODE + DescendantOf.DEPTH_SEPARATOR + 2);

    assertEquals(
        expectedTokens,
        DescendantOf.getDepthTokens(List.of(DEPTH_ANCHOR_CODE), ONE_ANCHOR_MAX_LEVEL));
  }

  /** Depth tokens that exactly reach the limit are accepted. */
  @Test
  public void testDepthTokensAtLimitAreAccepted() {
    final int anchorsAtLimit = MatchAnyOf.MAX_VALUES / LEVELS_AT_LIMIT;

    assertEquals(
        MatchAnyOf.MAX_VALUES,
        DescendantOf.getDepthTokens(getCodes(anchorsAtLimit), LEVELS_AT_LIMIT).size());
  }

  /** Too many anchors for the limit, even at a max level of one, throws an error. */
  @Test
  public void testDepthTokensWithTooManyAnchorsThrowsError() {
    assertThrows(
        IllegalArgumentException.class,
        () -> DescendantOf.getDepthTokens(getCodes(ANCHORS_OVER_LIMIT), ONE_LEVEL));
  }

  /** Anchors times max level over the limit throws an error naming the max level. */
  @Test
  public void testDepthTokensOverLimitThroughMaxLevelThrowsErrorNamingMaxLevel() {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> DescendantOf.getDepthTokens(getCodes(ANCHORS_UNDER_LIMIT), LEVELS_OVER_LIMIT));

    assertTrue(e.getMessage().contains(MAX_LEVEL_MESSAGE), e.getMessage());
  }

  /** A null anchor query on DescendantOf throws an error naming anchorQuery. */
  @Test
  public void testDescendantOfWithNullAnchorQueryThrowsErrorNamingAnchorQuery() {
    final NullPointerException e =
        assertThrows(
            NullPointerException.class, () -> new DescendantOf(terminologies, (EvsQuery) null));

    assertEquals(ANCHOR_QUERY_ARGUMENT, e.getMessage());
  }

  /** A null anchor query on AncestorOrSelfOf throws an error naming anchorQuery. */
  @Test
  public void testAncestorOrSelfOfWithNullAnchorQueryThrowsErrorNamingAnchorQuery() {
    final NullPointerException e =
        assertThrows(
            NullPointerException.class,
            () -> new AncestorOrSelfOf(terminologies, (EvsQuery) null, ONE_LEVEL));

    assertEquals(ANCHOR_QUERY_ARGUMENT, e.getMessage());
  }

  /** A null code on AncestorOf throws an error naming code. */
  @Test
  public void testAncestorOfWithNullCodeThrowsErrorNamingCode() {
    final NullPointerException e =
        assertThrows(
            NullPointerException.class, () -> new AncestorOf(terminologies, (String) null));

    assertEquals(CODE_ARGUMENT, e.getMessage());
  }

  /** A null code on DescendantOrSelfOf throws an error naming code. */
  @Test
  public void testDescendantOrSelfOfWithNullCodeThrowsErrorNamingCode() {
    final NullPointerException e =
        assertThrows(
            NullPointerException.class,
            () -> new DescendantOrSelfOf(terminologies, (String) null, ONE_LEVEL));

    assertEquals(CODE_ARGUMENT, e.getMessage());
  }

  /**
   * On an index without ancestor fields, a branch with more descendants than fit in one query
   * throws an error telling the caller to reindex.
   */
  @Test
  public void testFallbackWithTooManyDescendantsThrowsErrorAskingToReindex() {
    stubSearch(
        anchor -> {
          final Concept concept = getConcept(anchor);
          concept.setDescendants(getCodes(TOO_MANY).stream().map(Concept::new).toList());
          return concept;
        });

    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new DescendantOf(terminologies, DESCENDANT_ANCHOR_CODE)
                    .fetchOpenSearchQuery(operations));

    assertTrue(e.getMessage().contains(REINDEX_MESSAGE), e.getMessage());
  }

  /** The same limit applies to the fallback's depth-limited walk over children. */
  @Test
  public void testFallbackWithMaxLevelAndTooManyChildrenThrowsError() {
    stubSearch(
        anchor -> {
          final Concept concept = getConcept(anchor);
          concept.setChildren(getCodes(TOO_MANY).stream().map(Concept::new).toList());
          return concept;
        });

    assertThrows(
        IllegalArgumentException.class,
        () ->
            new DescendantOf(terminologies, DESCENDANT_ANCHOR_CODE, ONE_LEVEL)
                .fetchOpenSearchQuery(operations));
  }

  /** An anchor with more ancestors than fit in one query throws an error. */
  @Test
  public void testAncestorOfWithTooManyAncestorsThrowsErrorNamingTooManyAncestors() {
    stubSearch(anchor -> getConceptWithAncestorCount(anchor, TOO_MANY));

    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                new AncestorOf(terminologies, ANCESTOR_ANCHOR_CODE)
                    .fetchOpenSearchQuery(operations));

    assertTrue(e.getMessage().contains(TOO_MANY_ANCESTORS_MESSAGE), e.getMessage());
  }

  /** The fallback finds the descendants of every one of many anchors. */
  @Test
  public void testFallbackWithManyAnchorsFindsEveryAnchorsDescendants() {
    stubOneChildPerAnchor();

    final Set<String> found =
        StoredHierarchyFallback.fetchDescendantCodes(
            operations, terminology, getCodes(MANY_ANCHORS), null);

    assertEquals(MANY_ANCHORS, found.size());
    assertTrue(
        found.containsAll(
            Set.of(FIRST_ANCHOR_CODE + CHILD_SUFFIX, LAST_ANCHOR_CODE + CHILD_SUFFIX)));
  }

  /** The fallback reads many anchors' descendants in lookups of at most CONCEPTS_PER_LOOKUP. */
  @Test
  public void testFallbackWithManyAnchorsLooksUpInBatchesOfConceptsPerLookup() {
    stubOneChildPerAnchor();

    StoredHierarchyFallback.fetchDescendantCodes(
        operations, terminology, getCodes(MANY_ANCHORS), null);

    assertEquals(EXPECTED_LOOKUP_SIZES, getLookupSizes(EXPECTED_LOOKUP_COUNT));
  }

  /** Stored paths for many anchors give the ancestors of every one of them. */
  @Test
  public void testStoredPathsWithManyAnchorsFindsEveryAnchorsAncestors() {
    stubOneParentPerAnchor();

    final Set<String> found =
        StoredPaths.fetchAncestorCodes(operations, terminology, getCodes(MANY_ANCHORS), null);

    assertEquals(MANY_ANCHORS, found.size());
    assertTrue(
        found.containsAll(
            Set.of(FIRST_ANCHOR_CODE + PARENT_SUFFIX, LAST_ANCHOR_CODE + PARENT_SUFFIX)));
  }

  /** Stored paths for many anchors are read in lookups of at most CONCEPTS_PER_LOOKUP. */
  @Test
  public void testStoredPathsWithManyAnchorsLooksUpInBatchesOfConceptsPerLookup() {
    stubOneParentPerAnchor();

    StoredPaths.fetchAncestorCodes(operations, terminology, getCodes(MANY_ANCHORS), null);

    assertEquals(EXPECTED_LOOKUP_SIZES, getLookupSizes(EXPECTED_LOOKUP_COUNT));
  }

  /** Stubs every anchor to have exactly one made-up child. */
  private void stubOneChildPerAnchor() {
    stubSearch(
        anchor -> {
          final Concept concept = getConcept(anchor);
          concept.setDescendants(List.of(new Concept(anchor + CHILD_SUFFIX)));
          return concept;
        });
  }

  /** Stubs every anchor to have one stored path to a single made-up parent. */
  private void stubOneParentPerAnchor() {
    stubSearch(
        anchor -> {
          final Concept concept = getConcept(anchor);
          final List<ConceptMinimal> steps =
              List.of(getPathStep(anchor, 0), getPathStep(anchor + PARENT_SUFFIX, 1));
          concept.setPaths(new Paths(List.of(new Path(1, steps))));
          return concept;
        });
  }

  /**
   * Stubs the mock: a lookup by ids returns one concept per id, built by {@code byCode}; any other
   * search (the inner query, run through {@link EvsQuery#declareDependencies}) returns one page
   * with the matched anchor.
   *
   * @param byCode builds the stored concept for a code
   */
  private void stubSearch(final Function<String, Concept> byCode) {
    when(operations.search(any(Query.class), eq(Concept.class), any(IndexCoordinates.class)))
        .thenAnswer(
            invocation -> {
              final NativeSearchQuery search = invocation.getArgument(0);

              // The inner query matched the anchor by code; the mock ignores which code
              if (!(search.getQuery() instanceof IdsQueryBuilder ids)) {
                return getPage(List.of(getConcept(MATCHED_ANCHOR_CODE)));
              }

              return getPage(ids.ids().stream().map(byCode).toList());
            });
  }

  /**
   * Returns the number of ids in each lookup by ids, in order, after checking how many ran.
   *
   * @param expectedLookups how many lookups by ids should have run
   * @return the number of ids in each
   */
  private List<Integer> getLookupSizes(final int expectedLookups) {
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
  private static Set<String> getCodes(final int count) {
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
  private static Concept getConcept(final String code) {
    final Concept concept = new Concept(code);
    concept.setTerminology(TERMINOLOGY);
    concept.setVersion(VERSION);
    return concept;
  }

  /**
   * Builds a concept with one stored path to root that holds the given number of ancestors.
   *
   * @param code the concept's code, the first step of the path
   * @param ancestorCount how many ancestors the path holds after the concept itself
   * @return the concept
   */
  private static Concept getConceptWithAncestorCount(final String code, final int ancestorCount) {
    final List<ConceptMinimal> steps = new ArrayList<>();
    steps.add(getPathStep(code, 0));
    int level = 1;
    for (final String ancestorCode : getCodes(ancestorCount)) {
      steps.add(getPathStep(ancestorCode, level++));
    }

    final Concept concept = getConcept(code);
    concept.setPaths(new Paths(List.of(new Path(1, steps))));
    return concept;
  }

  /**
   * Builds one step of a path to root.
   *
   * @param code the step's code
   * @param level its distance from the path's first concept
   * @return the step
   */
  private static ConceptMinimal getPathStep(final String code, final int level) {
    final ConceptMinimal step = new ConceptMinimal(code);
    step.setLevel(level);
    return step;
  }

  /**
   * Builds one page of hits holding the given concepts, sorted like the real concept-key page
   * search.
   *
   * @param concepts the concepts
   * @return the page
   */
  private static SearchHits<Concept> getPage(final Collection<Concept> concepts) {
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
