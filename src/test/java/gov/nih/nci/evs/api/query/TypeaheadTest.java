package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.IncludeParam;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import gov.nih.nci.evs.api.util.ConceptUtils;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.core.OpenSearchOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A tutorial for {@link Typeahead} ranking, using concepts built to isolate one ranking rule each.
 *
 * <p>Every lesson follows the same steps:
 *
 * <ol>
 *   <li>A small index holds the concepts in {@link #INDEXED_CONCEPTS}, mapped exactly like a real
 *       concept index. Each concept gets a normalized name and a chosen number of descendants,
 *       which are the inputs to the ranking.
 *   <li>The lesson runs a typeahead for what the user has typed so far.
 *   <li>The lesson checks the order the matches came back in.
 * </ol>
 *
 * <p>This test makes its own index, so it does not depend on the concept indexes being reindexed or
 * on what the NCI Thesaurus currently contains.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TypeaheadTest {

  /** A throwaway index used only by this tutorial. */
  private static final String INDEX_NAME = "evs_typeahead_tutorial";

  /** One page large enough to hold every indexed concept. */
  private static final Pageable ALL_CONCEPTS = PageRequest.of(0, 100);

  /** The fields to return for each hit. */
  private static final IncludeParam MINIMAL_FIELDS = new IncludeParam("minimal");

  /**
   * The concepts in the index. Concepts that share a lesson share a typed word that no other
   * concept in the index has, so each lesson only sees its own concepts.
   */
  private static final List<IndexedConcept> INDEXED_CONCEPTS =
      List.of(
          // Lesson 1: more descendants rank first
          new IndexedConcept("C01", "Lung Carcinoma", 4),
          new IndexedConcept("C02", "Lung Cancer", 0),
          // Lesson 2: fewer words rank first
          new IndexedConcept("C03", "Melanoma", 0),
          new IndexedConcept("C04", "Melanoma of Skin", 0),
          // Lesson 3: names starting with the typed text rank first
          new IndexedConcept("C05", "Blue Nevus", 0),
          new IndexedConcept("C06", "Nevus Spilus", 0),
          // Lesson 4: enough descendants outweigh the starts-with bonus
          new IndexedConcept("C07", "Lymphoblastic Lymphoma", 0),
          new IndexedConcept("C08", "Acute Lymphoblastic Leukemia", 20),
          // Lesson 5: a concept without a normalized name ranks last
          IndexedConcept.withoutNormalizedName("C09", "Sarcoma"),
          new IndexedConcept("C10", "Kaposi Sarcoma", 0),
          // Lesson 6: equal ranks fall back to code order
          new IndexedConcept("C12", "Adenoma", 0),
          new IndexedConcept("C11", "Adenoma", 0));

  /** The project's OpenSearch wrapper, which selects the correct operations bean. */
  @Autowired private OpensearchOperationsService operationsService;

  /** The OpenSearch operations, taken from {@link #operationsService}. */
  private OpenSearchOperations operations;

  /** The terminology whose concept index is the tutorial index. */
  private List<Terminology> terminologies;

  /** Creates the index, mapped like a concept index, and loads {@link #INDEXED_CONCEPTS}. */
  @BeforeAll
  public void setUpIndex() {
    operations = operationsService.getOpenSearchOperations();
    final IndexCoordinates index = IndexCoordinates.of(INDEX_NAME);
    final IndexOperations indexOperations = operations.indexOps(index);
    indexOperations.delete();
    indexOperations.create();
    indexOperations.putMapping(Concept.class);

    final List<Concept> documents =
        INDEXED_CONCEPTS.stream().map(IndexedConcept::getConcept).collect(Collectors.toList());
    operations.save(documents, index);
    indexOperations.refresh();

    final Terminology terminology = new Terminology();
    terminology.setIndexName(INDEX_NAME);
    terminologies = List.of(terminology);
  }

  /** Removes the index. */
  @AfterAll
  public void tearDownIndex() {
    operations.indexOps(IndexCoordinates.of(INDEX_NAME)).delete();
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 0: typeahead needs at least one word to complete.
  // ---------------------------------------------------------------------------------------------

  /** Null, blank, and punctuation-only text has no words. */
  @Test
  public void testTextWithoutWordsHasNoWords() {
    for (final String incompleteTerm : new String[] {null, "", "   ", "---", " , . "}) {
      assertFalse(Typeahead.getHasWordsToComplete(incompleteTerm), "[" + incompleteTerm + "]");
    }
  }

  /** A partial word counts as a word, even with punctuation around it. */
  @Test
  public void testPartialWordHasWords() {
    for (final String incompleteTerm : new String[] {"m", "melan", " -melan- "}) {
      assertTrue(Typeahead.getHasWordsToComplete(incompleteTerm), "[" + incompleteTerm + "]");
    }
  }

  /** A typeahead can't be built for text without words. */
  @Test
  public void testTypeaheadRejectsTextWithoutWords() {
    assertThrows(IllegalArgumentException.class, () -> new Typeahead(terminologies, "---"));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 1: general concepts (more descendants) rank first.
  // ---------------------------------------------------------------------------------------------

  /** Both names have two words and start with "lung ca", so the one with descendants wins. */
  @Test
  public void testMoreDescendantsRankFirst() {
    assertEquals(List.of("Lung Carcinoma", "Lung Cancer"), fetchRankedNames("lung ca"));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: short names rank first.
  // ---------------------------------------------------------------------------------------------

  /** Neither concept has descendants and both start with "melan", so the shorter name wins. */
  @Test
  public void testFewerWordsRankFirst() {
    assertEquals(List.of("Melanoma", "Melanoma of Skin"), fetchRankedNames("melan"));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: names starting with the typed text get a small bonus.
  // ---------------------------------------------------------------------------------------------

  /** Same descendants and word count, so the name starting with "nevus" wins. */
  @Test
  public void testStartsWithRanksFirst() {
    assertEquals(List.of("Nevus Spilus", "Blue Nevus"), fetchRankedNames("nevus"));
  }

  /**
   * The starts-with bonus is small. A much more general concept that only contains the typed word
   * still ranks above a name that starts with it, even with an extra word.
   */
  @Test
  public void testDescendantsOutweighStartsWith() {
    assertEquals(
        List.of("Acute Lymphoblastic Leukemia", "Lymphoblastic Lymphoma"),
        fetchRankedNames("lymphoblastic"));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: edge cases.
  // ---------------------------------------------------------------------------------------------

  /** A concept without a normalized name can't be ranked, so it comes last. */
  @Test
  public void testMissingNormalizedNameRanksLast() {
    assertEquals(List.of("Kaposi Sarcoma", "Sarcoma"), fetchRankedNames("sarcoma"));
  }

  /** Concepts that rank the same come back in code order, so results are stable. */
  @Test
  public void testEqualRanksFallBackToCodeOrder() {
    assertEquals(List.of("C11", "C12"), fetchRanked("adenoma", Concept::getCode));
  }

  /**
   * Runs a typeahead against the tutorial index.
   *
   * @param incompleteTerm the text typed so far
   * @return the names of the matches, best first
   */
  private List<String> fetchRankedNames(final String incompleteTerm) {
    return fetchRanked(incompleteTerm, Concept::getName);
  }

  /**
   * Runs a typeahead against the tutorial index.
   *
   * @param incompleteTerm the text typed so far
   * @param getValue extracts the value to report from each match
   * @return the values of the matches, best first
   */
  private List<String> fetchRanked(
      final String incompleteTerm, final Function<Concept, String> getValue) {
    final Typeahead typeahead = new Typeahead(terminologies, incompleteTerm);
    final NativeSearchQuery search =
        typeahead.getNativeSearchQuery(ALL_CONCEPTS, MINIMAL_FIELDS, operations);
    return operations.search(search, Concept.class, typeahead.getIndexCoordinates()).stream()
        .map(SearchHit::getContent)
        .map(getValue)
        .collect(Collectors.toList());
  }

  /** A concept in the tutorial index, with the inputs the ranking reads. */
  private static final class IndexedConcept {

    /** The concept to index. */
    private final Concept concept;

    /**
     * Instantiates an {@link IndexedConcept} with a normalized name.
     *
     * @param code the code
     * @param name the preferred name
     * @param descendantCount the number of descendants
     */
    IndexedConcept(final String code, final String name, final int descendantCount) {
      this(code, name, descendantCount, ConceptUtils.normalize(name));
    }

    /**
     * Instantiates an {@link IndexedConcept}.
     *
     * @param code the code
     * @param name the preferred name
     * @param descendantCount the number of descendants
     * @param normalizedName the normalized name, or null for none
     */
    private IndexedConcept(
        final String code,
        final String name,
        final int descendantCount,
        final String normalizedName) {
      concept = new Concept();
      concept.setCode(code);
      concept.setName(name);
      concept.setNormName(normalizedName);
      concept.setDescendantCount(descendantCount);
    }

    /**
     * Instantiates an {@link IndexedConcept} without a normalized name and without descendants.
     *
     * @param code the code
     * @param name the preferred name
     * @return the indexed concept
     */
    static IndexedConcept withoutNormalizedName(final String code, final String name) {
      return new IndexedConcept(code, name, 0, null);
    }

    /**
     * Returns the concept to index.
     *
     * @return the concept
     */
    Concept getConcept() {
      return concept;
    }
  }
}
