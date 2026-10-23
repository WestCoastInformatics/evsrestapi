package gov.nih.nci.evs.api.query;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.ConceptMinimal;
import gov.nih.nci.evs.api.model.Path;
import gov.nih.nci.evs.api.model.Paths;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.model.TerminologyMetadata;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tutorial for the hierarchy nodes {@link DescendantOf} and {@link DescendantOrSelfOf}, run against
 * two throwaway indexes holding the same small hierarchy:
 *
 * <pre>
 * R
 * ├─ A
 * │  ├─ C
 * │  │  └─ E
 * │  │     └─ G
 * │  └─ D
 * │     └─ F      F has two parents, D and B
 * └─ B
 *    └─ F
 * </pre>
 *
 * <p>The <b>indexed</b> copy has {@code ancestors} and {@code ancestorDepths}, as reindexed
 * terminologies do. The <b>legacy</b> copy only has the stored {@code children} and depth-first
 * {@code descendants}, as terminologies indexed before those fields do, so it exercises the
 * fallback. Every lesson checks both copies give the same answer.
 *
 * <p>F is the polyhierarchy case: it is 2 below R through B, but a depth-first walk reaches it
 * through A and D first, at 3. Depth limits must use the shortest distance.
 *
 * <p>Runs against OpenSearch on port 9201, like {@link AtomicQueryTest}.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HierarchyQueryTest {

  /** The hierarchy: each code's children, in order. */
  private static final Map<String, List<String>> CHILDREN = new LinkedHashMap<>();

  static {
    CHILDREN.put("R", List.of("A", "B"));
    CHILDREN.put("A", List.of("C", "D"));
    CHILDREN.put("B", List.of("F"));
    CHILDREN.put("C", List.of("E"));
    CHILDREN.put("D", List.of("F"));
    CHILDREN.put("E", List.of("G"));
    CHILDREN.put("F", List.of());
    CHILDREN.put("G", List.of());
  }

  /** Codes tagged with a shared keyword so an inner query can pick several anchors at once. */
  private static final Set<String> GROUPED = Set.of("B", "C");

  /** Keyword field holding the group tag. */
  private static final String GROUP_FIELD = "normName";

  /** The group tag. */
  private static final String GROUP_TAG = "anchor-group";

  /** Terminology with ancestor fields indexed. */
  private Terminology indexed;

  /** Terminology indexed before ancestor fields existed. */
  private Terminology legacy;

  /** The project's OpenSearch wrapper, which selects the correct operations bean. */
  @Autowired private OpensearchOperationsService operationsService;

  /** The OpenSearch operations. */
  private OpenSearchOperations operations;

  /** Creates both indexes and loads the hierarchy into each. */
  @BeforeAll
  public void setUpIndexes() {
    operations = operationsService.getOpenSearchOperations();
    indexed = terminology("1", "evs_hierarchy_query_indexed", true);
    legacy = terminology("2", "evs_hierarchy_query_legacy", null);
    load(indexed, true);
    load(legacy, false);
  }

  /** Removes both indexes. */
  @AfterAll
  public void tearDownIndexes() {
    operations.indexOps(IndexCoordinates.of(indexed.getIndexName())).delete();
    operations.indexOps(IndexCoordinates.of(legacy.getIndexName())).delete();
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 1: DescendantOf matches everything strictly below the anchor.
  // ---------------------------------------------------------------------------------------------

  /** Everything below A, at any depth, and not A itself. */
  @Test
  public void testDescendantOfMatchesEverythingBelow() {
    assertBoth(t -> new DescendantOf(t, "A"), "C", "D", "E", "F", "G");
  }

  /** A leaf has nothing below it. */
  @Test
  public void testDescendantOfLeafMatchesNothing() {
    assertBoth(t -> new DescendantOf(t, "G"));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: DescendantOrSelfOf also matches the anchor.
  // ---------------------------------------------------------------------------------------------

  /** A and everything below A. */
  @Test
  public void testDescendantOrSelfOfIncludesAnchor() {
    assertBoth(t -> new DescendantOrSelfOf(t, "A"), "A", "C", "D", "E", "F", "G");
  }

  /** A leaf matches only itself. */
  @Test
  public void testDescendantOrSelfOfLeafMatchesItself() {
    assertBoth(t -> new DescendantOrSelfOf(t, "G"), "G");
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: maxLevel limits depth by shortest distance.
  // ---------------------------------------------------------------------------------------------

  /** maxLevel 1 is the direct children. */
  @Test
  public void testMaxLevelOneIsChildren() {
    assertBoth(t -> new DescendantOf(t, "A", 1), "C", "D");
  }

  /**
   * F is 2 below R through B, though a depth-first walk reaches it at 3 through A and D. maxLevel 2
   * must include it, and must leave out E (3 below R) and G (4).
   */
  @Test
  public void testMaxLevelUsesShortestDistance() {
    assertBoth(t -> new DescendantOf(t, "R", 2), "A", "B", "C", "D", "F");
  }

  /** With the anchor included, the anchor is depth 0 and always matches. */
  @Test
  public void testMaxLevelWithSelf() {
    assertBoth(t -> new DescendantOrSelfOf(t, "A", 1), "A", "C", "D");
  }

  /** maxLevel must be at least 1. */
  @Test
  public void testMaxLevelMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> new DescendantOf(List.of(indexed), "A", 0));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: the anchors come from any inner query, run first as a dependency.
  // ---------------------------------------------------------------------------------------------

  /** An inner query matching B and C: below either of them. */
  @Test
  public void testInnerQueryWithSeveralAnchors() {
    assertBoth(t -> new DescendantOf(t, new Match(t, GROUP_FIELD, GROUP_TAG)), "E", "F", "G");
  }

  /** An inner query matching nothing gives nothing. */
  @Test
  public void testInnerQueryWithNoAnchors() {
    assertBoth(t -> new DescendantOf(t, new Match(t, GROUP_FIELD, "no-such-tag")));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 5: hierarchy nodes nest and compose.
  // ---------------------------------------------------------------------------------------------

  /** Below the children of R: the inner query is itself a hierarchy node. */
  @Test
  public void testNestedDescendantOf() {
    assertBoth(t -> new DescendantOf(t, new DescendantOf(t, "R", 1)), "C", "D", "E", "F", "G");
  }

  /** Two levels of nesting with depth limits at each. */
  @Test
  public void testDoublyNestedWithDepths() {
    // Children of R are A and B; their children are C, D, and F; those and one more level below
    assertBoth(
        t -> new DescendantOrSelfOf(t, new DescendantOf(t, new DescendantOf(t, "R", 1), 1), 1),
        "C",
        "D",
        "E",
        "F");
  }

  /** Composes inside a raw bool, as other nodes will: below A but not below C. */
  @Test
  public void testComposesInBoolQuery() {
    for (final Terminology terminology : List.of(indexed, legacy)) {
      final List<Terminology> t = List.of(terminology);
      final Set<String> codes =
          search(
              t,
              QueryBuilders.boolQuery()
                  .must(new DescendantOf(t, "A").fetchOpenSearchQuery(operations))
                  .mustNot(new DescendantOf(t, "C").fetchOpenSearchQuery(operations)));
      assertThat(codes).as(terminology.getIndexName()).containsExactlyInAnyOrder("C", "D", "F");
    }
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 6: several terminologies at once, indexed and legacy mixed.
  // ---------------------------------------------------------------------------------------------

  /** Each terminology's anchors match only in its own index, through its own path. */
  @Test
  public void testIndexedAndLegacyTogether() {
    final List<Terminology> both = List.of(indexed, legacy);
    final DescendantOf query = new DescendantOf(both, "A");
    final Map<String, Set<String>> byIndex = new HashMap<>();
    for (final SearchHit<Concept> hit : searchHits(both, query.fetchOpenSearchQuery(operations))) {
      byIndex.computeIfAbsent(hit.getIndex(), k -> new TreeSet<>()).add(hit.getContent().getCode());
    }
    final Set<String> expected = Set.of("C", "D", "E", "F", "G");
    assertThat(byIndex.get(indexed.getIndexName())).isEqualTo(expected);
    assertThat(byIndex.get(legacy.getIndexName())).isEqualTo(expected);
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 7: AncestorOf matches everything strictly above the anchor (the upward counterpart).
  // ---------------------------------------------------------------------------------------------

  /** Everything above G, and not G itself. */
  @Test
  public void testAncestorOfMatchesEverythingAbove() {
    assertBoth(t -> new AncestorOf(t, "G"), "E", "C", "A", "R");
  }

  /** F has two parents, so its ancestors come from both paths. */
  @Test
  public void testAncestorOfFollowsEveryPath() {
    assertBoth(t -> new AncestorOf(t, "F"), "D", "B", "A", "R");
  }

  /** The root has nothing above it. */
  @Test
  public void testAncestorOfRootMatchesNothing() {
    assertBoth(t -> new AncestorOf(t, "R"));
  }

  /** AncestorOrSelfOf also matches the anchor; the root matches only itself. */
  @Test
  public void testAncestorOrSelfOfIncludesAnchor() {
    assertBoth(t -> new AncestorOrSelfOf(t, "G"), "G", "E", "C", "A", "R");
    assertBoth(t -> new AncestorOrSelfOf(t, "R"), "R");
  }

  /** maxLevel 1 is the direct parents. */
  @Test
  public void testAncestorMaxLevelOneIsParents() {
    assertBoth(t -> new AncestorOf(t, "F", 1), "D", "B");
    assertBoth(t -> new AncestorOrSelfOf(t, "F", 1), "F", "D", "B");
  }

  /**
   * R is 2 above F through B, though 3 through D and A. maxLevel 2 must include it, by shortest
   * distance.
   */
  @Test
  public void testAncestorMaxLevelUsesShortestDistance() {
    assertBoth(t -> new AncestorOf(t, "F", 2), "D", "B", "A", "R");
    assertBoth(t -> new AncestorOf(t, "G", 2), "E", "C");
  }

  /** maxLevel must be at least 1. */
  @Test
  public void testAncestorMaxLevelMustBePositive() {
    assertThrows(IllegalArgumentException.class, () -> new AncestorOf(List.of(indexed), "G", 0));
  }

  /** An inner query matching B and C: above either of them. */
  @Test
  public void testAncestorOfInnerQueryWithSeveralAnchors() {
    assertBoth(t -> new AncestorOf(t, new Match(t, GROUP_FIELD, GROUP_TAG)), "A", "R");
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 8: upward and downward nodes nest in each other and compose.
  // ---------------------------------------------------------------------------------------------

  /** Above the children of A (C and D): A and R. */
  @Test
  public void testAncestorOfDescendants() {
    assertBoth(t -> new AncestorOf(t, new DescendantOf(t, "A", 1)), "A", "R");
  }

  /** Below the parent of G (E): G. */
  @Test
  public void testDescendantOfAncestors() {
    assertBoth(t -> new DescendantOf(t, new AncestorOf(t, "G", 1)), "G");
  }

  /** Siblings of C, and C: the children of C's parents. */
  @Test
  public void testSiblingsOrSelf() {
    assertBoth(t -> new DescendantOf(t, new AncestorOf(t, "C", 1), 1), "C", "D");
  }

  /** Common ancestors of G and F, composed in a raw bool. */
  @Test
  public void testCommonAncestorsInBoolQuery() {
    for (final Terminology terminology : List.of(indexed, legacy)) {
      final List<Terminology> t = List.of(terminology);
      final Set<String> codes =
          search(
              t,
              QueryBuilders.boolQuery()
                  .must(new AncestorOf(t, "G").fetchOpenSearchQuery(operations))
                  .must(new AncestorOf(t, "F").fetchOpenSearchQuery(operations)));
      assertThat(codes).as(terminology.getIndexName()).containsExactlyInAnyOrder("A", "R");
    }
  }

  /** Each terminology's anchors match only in its own index. */
  @Test
  public void testAncestorOfIndexedAndLegacyTogether() {
    final List<Terminology> both = List.of(indexed, legacy);
    final Map<String, Set<String>> byIndex = new HashMap<>();
    for (final SearchHit<Concept> hit :
        searchHits(both, new AncestorOf(both, "G").fetchOpenSearchQuery(operations))) {
      byIndex.computeIfAbsent(hit.getIndex(), k -> new TreeSet<>()).add(hit.getContent().getCode());
    }
    final Set<String> expected = Set.of("E", "C", "A", "R");
    assertThat(byIndex.get(indexed.getIndexName())).isEqualTo(expected);
    assertThat(byIndex.get(legacy.getIndexName())).isEqualTo(expected);
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /** Builds a query for a list of terminologies. */
  private interface QueryFactory {
    EvsQuery build(List<Terminology> terminologies);
  }

  /**
   * Asserts the query matches exactly the expected codes in the indexed copy and in the legacy
   * copy.
   *
   * @param factory builds the query for one terminology
   * @param expected the expected codes
   */
  private void assertBoth(final QueryFactory factory, final String... expected) {
    for (final Terminology terminology : List.of(indexed, legacy)) {
      final List<Terminology> t = List.of(terminology);
      final Set<String> codes = search(t, factory.build(t).fetchOpenSearchQuery(operations));
      assertThat(codes).as(terminology.getIndexName()).containsExactlyInAnyOrder(expected);
    }
  }

  /**
   * Runs a query and returns the matching codes.
   *
   * @param terminologies the terminologies to search
   * @param query the query
   * @return the codes
   */
  private Set<String> search(
      final List<Terminology> terminologies, final org.opensearch.index.query.QueryBuilder query) {
    return searchHits(terminologies, query).stream()
        .map(hit -> hit.getContent().getCode())
        .collect(Collectors.toSet());
  }

  /**
   * Runs a query and returns the hits.
   *
   * @param terminologies the terminologies to search
   * @param query the query
   * @return the hits
   */
  private List<SearchHit<Concept>> searchHits(
      final List<Terminology> terminologies, final org.opensearch.index.query.QueryBuilder query) {
    final IndexCoordinates indexes =
        IndexCoordinates.of(
            terminologies.stream().map(Terminology::getIndexName).toArray(String[]::new));
    return operations
        .search(
            new NativeSearchQueryBuilder()
                .withQuery(query)
                .withPageable(PageRequest.of(0, 100))
                .build(),
            Concept.class,
            indexes)
        .getSearchHits();
  }

  /**
   * Returns a test terminology.
   *
   * @param version the version
   * @param indexName the index name
   * @param hierarchyFields the hierarchy fields flag
   * @return the terminology
   */
  private static Terminology terminology(
      final String version, final String indexName, final Boolean hierarchyFields) {
    final Terminology terminology = new Terminology();
    terminology.setTerminology("unit");
    terminology.setVersion(version);
    terminology.setIndexName(indexName);
    final TerminologyMetadata metadata = new TerminologyMetadata();
    metadata.setHierarchy(true);
    metadata.setHierarchyFields(hierarchyFields);
    terminology.setMetadata(metadata);
    return terminology;
  }

  /**
   * Creates a terminology's index with the concept mapping and loads the hierarchy.
   *
   * @param terminology the terminology
   * @param withAncestorFields whether to set ancestors and ancestorDepths
   */
  private void load(final Terminology terminology, final boolean withAncestorFields) {
    final IndexCoordinates index = IndexCoordinates.of(terminology.getIndexName());
    final IndexOperations indexOperations = operations.indexOps(index);
    indexOperations.delete();
    indexOperations.create();
    indexOperations.putMapping(indexOperations.createMapping(Concept.class));

    final List<Concept> concepts = new ArrayList<>();
    for (final String code : CHILDREN.keySet()) {
      final Concept concept = new Concept(terminology.getTerminology(), code, "Concept " + code);
      concept.setVersion(terminology.getVersion());
      concept.setNormName(GROUPED.contains(code) ? GROUP_TAG : code.toLowerCase());
      concept.setChildren(
          CHILDREN.get(code).stream().map(Concept::new).collect(Collectors.toList()));
      concept.setDescendants(depthFirstDescendants(code));
      concept.setPaths(pathsToRoot(code));
      if (withAncestorFields) {
        final Map<String, Integer> depths = shortestAncestorDepths(code);
        concept.setAncestors(new ArrayList<>(new TreeSet<>(depths.keySet())));
        concept.setAncestorDepths(
            depths.entrySet().stream()
                .map(e -> e.getKey() + DescendantOf.DEPTH_SEPARATOR + e.getValue())
                .sorted()
                .collect(Collectors.toList()));
      }
      concepts.add(concept);
    }
    operations.save(concepts, index);
    indexOperations.refresh();
  }

  /**
   * Returns a code's descendants as the loader stores them: depth-first, each at the depth the walk
   * first reached it.
   *
   * @param code the code
   * @return the descendants
   */
  private static List<Concept> depthFirstDescendants(final String code) {
    final Map<String, Integer> levels = new LinkedHashMap<>();
    depthFirst(code, 1, levels);
    final List<Concept> descendants = new ArrayList<>();
    levels.forEach(
        (c, level) -> {
          final Concept descendant = new Concept(c);
          descendant.setLevel(level);
          descendants.add(descendant);
        });
    return descendants;
  }

  /**
   * Walks the hierarchy depth-first, keeping the first depth each code is reached at.
   *
   * @param code the code to walk below
   * @param level the depth of its children
   * @param levels the codes reached so far, with their depths
   */
  private static void depthFirst(
      final String code, final int level, final Map<String, Integer> levels) {
    for (final String child : CHILDREN.get(code)) {
      levels.putIfAbsent(child, level);
      depthFirst(child, level + 1, levels);
    }
  }

  /**
   * Returns each ancestor of a code with its shortest distance, by walking parents breadth-first.
   *
   * @param code the code
   * @return the ancestors and their depths
   */
  private static Map<String, Integer> shortestAncestorDepths(final String code) {
    final Map<String, Set<String>> parents = new HashMap<>();
    CHILDREN.forEach(
        (parent, children) ->
            children.forEach(
                child -> parents.computeIfAbsent(child, k -> new LinkedHashSet<>()).add(parent)));
    final Map<String, Integer> depths = new LinkedHashMap<>();
    final Deque<String> queue = new ArrayDeque<>(List.of(code));
    final Map<String, Integer> distance = new HashMap<>(Map.of(code, 0));
    while (!queue.isEmpty()) {
      final String current = queue.removeFirst();
      for (final String parent : parents.getOrDefault(current, Set.of())) {
        if (!distance.containsKey(parent)) {
          distance.put(parent, distance.get(current) + 1);
          depths.put(parent, distance.get(parent));
          queue.addLast(parent);
        }
      }
    }
    return depths;
  }

  /**
   * Returns a code's paths to root as the loader stores them: each path starts at the code (level
   * 0) and ends at a root.
   *
   * @param code the code
   * @return the paths
   */
  private static Paths pathsToRoot(final String code) {
    final Map<String, List<String>> parents = new HashMap<>();
    CHILDREN.forEach(
        (parent, children) ->
            children.forEach(
                child -> parents.computeIfAbsent(child, k -> new ArrayList<>()).add(parent)));
    final List<List<String>> codePaths = new ArrayList<>();
    collectPaths(code, new ArrayList<>(List.of(code)), parents, codePaths);
    final Paths paths = new Paths();
    paths.setPaths(new ArrayList<>());
    for (final List<String> codePath : codePaths) {
      final Path path = new Path();
      path.setDirection(1);
      for (int level = 0; level < codePath.size(); level++) {
        final ConceptMinimal step = new ConceptMinimal(codePath.get(level));
        step.setLevel(level);
        path.getConcepts().add(step);
      }
      paths.getPaths().add(path);
    }
    return paths;
  }

  /**
   * Collects every path from a code up to a root.
   *
   * @param code the current code
   * @param soFar the path so far, ending at the current code
   * @param parents each code's parents
   * @param codePaths the completed paths
   */
  private static void collectPaths(
      final String code,
      final List<String> soFar,
      final Map<String, List<String>> parents,
      final List<List<String>> codePaths) {
    final List<String> up = parents.getOrDefault(code, List.of());
    if (up.isEmpty()) {
      codePaths.add(new ArrayList<>(soFar));
      return;
    }
    for (final String parent : up) {
      soFar.add(parent);
      collectPaths(parent, soFar, parents, codePaths);
      soFar.remove(soFar.size() - 1);
    }
  }
}
