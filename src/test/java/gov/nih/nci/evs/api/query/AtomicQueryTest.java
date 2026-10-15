package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.data.client.orhlc.NativeSearchQuery;
import org.opensearch.data.client.orhlc.NativeSearchQueryBuilder;
import org.opensearch.data.core.OpenSearchOperations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A tutorial for the atomic queries: {@link Match}, {@link StartsWith}, {@link Contains}, and
 * {@link Fuzzy}, plus the {@link CaseInsensitive} wrapper.
 *
 * <p>Every lesson follows the same steps:
 *
 * <ol>
 *   <li>A small index holds the names in {@link #INDEXED_NAMES}, stored in a keyword field so each
 *       name is matched as one whole, case-preserved value.
 *   <li>The lesson builds a query, e.g. {@code new Contains(terminologies, "name", "lanoma")}.
 *   <li>The query runs against the index, and the lesson checks which names came back.
 * </ol>
 *
 * <p>Atomic queries don't filter by terminology, so every lesson passes an empty list.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class AtomicQueryTest {

  /** A throwaway index used only by this tutorial. */
  private static final IndexCoordinates INDEX = IndexCoordinates.of("evs_atomic_query_tutorial");

  /** One page large enough to hold every indexed name. */
  private static final Pageable ALL_NAMES = PageRequest.of(0, 100);

  /** The keyword field every lesson searches. */
  private static final String NAME_FIELD = "name";

  /** Atomic queries don't filter by terminology. */
  private static final List<Terminology> TERMINOLOGIES = List.of();

  /** The names in the index. Note the two spellings of melanoma that differ only by case. */
  private static final List<String> INDEXED_NAMES =
      List.of(
          "Melanoma", "melanoma", "Malignant Melanoma", "Melanocyte", "Carcinoma", "Stage 1*");

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
  // Lesson 1: Match finds values that are exactly equal to the term.
  // ---------------------------------------------------------------------------------------------

  /** Match returns only the value that is identical to the term. */
  @Test
  public void testMatchFindsIdenticalValue() {
    final EvsQuery query = new Match(TERMINOLOGIES, NAME_FIELD, "Melanoma");

    assertEquals(Set.of("Melanoma"), fetchMatchingNames(query));
  }

  /** Match is case sensitive: "melanoma" and "Melanoma" are different values. */
  @Test
  public void testMatchIsCaseSensitive() {
    final EvsQuery query = new Match(TERMINOLOGIES, NAME_FIELD, "melanoma");

    assertEquals(Set.of("melanoma"), fetchMatchingNames(query));
  }

  /** Match never matches part of a value. "Melan" is not equal to anything in the index. */
  @Test
  public void testMatchIgnoresPartialValues() {
    final EvsQuery query = new Match(TERMINOLOGIES, NAME_FIELD, "Melan");

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: StartsWith finds values that begin with the term.
  // ---------------------------------------------------------------------------------------------

  /**
   * StartsWith matches the beginning of the whole value, not of each word. "Malignant Melanoma"
   * contains a word starting with "Melano", but the value itself starts with "Malignant".
   */
  @Test
  public void testStartsWithFindsValuesWithPrefix() {
    final EvsQuery query = new StartsWith(TERMINOLOGIES, NAME_FIELD, "Melano");

    assertEquals(Set.of("Melanoma", "Melanocyte"), fetchMatchingNames(query));
  }

  /** StartsWith is case sensitive. */
  @Test
  public void testStartsWithIsCaseSensitive() {
    final EvsQuery query = new StartsWith(TERMINOLOGIES, NAME_FIELD, "melano");

    assertEquals(Set.of("melanoma"), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: Contains finds values that include the term anywhere.
  // ---------------------------------------------------------------------------------------------

  /** Contains matches the term at the start, middle, or end of a value. */
  @Test
  public void testContainsFindsTermAnywhere() {
    final EvsQuery query = new Contains(TERMINOLOGIES, NAME_FIELD, "lanoma");

    assertEquals(Set.of("Melanoma", "melanoma", "Malignant Melanoma"), fetchMatchingNames(query));
  }

  /** Contains is case sensitive, so the lowercase "melanoma" is not included. */
  @Test
  public void testContainsIsCaseSensitive() {
    final EvsQuery query = new Contains(TERMINOLOGIES, NAME_FIELD, "Melanoma");

    assertEquals(Set.of("Melanoma", "Malignant Melanoma"), fetchMatchingNames(query));
  }

  /**
   * Contains treats every character in the term literally. Searching for "*" finds only the value
   * that has an asterisk in it, rather than treating it as a wildcard that matches everything.
   */
  @Test
  public void testContainsTreatsWildcardCharactersLiterally() {
    final EvsQuery query = new Contains(TERMINOLOGIES, NAME_FIELD, "*");

    assertEquals(Set.of("Stage 1*"), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: Fuzzy finds values that are within a few typos of the term.
  // ---------------------------------------------------------------------------------------------

  /**
   * Fuzzy tolerates typos. "Carcinomma" is one extra letter away from "Carcinoma". By default the
   * number of allowed typos grows with the length of the term.
   */
  @Test
  public void testFuzzyToleratesTypos() {
    final EvsQuery query = new Fuzzy(TERMINOLOGIES, NAME_FIELD, "Carcinomma");

    assertEquals(Set.of("Carcinoma"), fetchMatchingNames(query));
  }

  /**
   * Fuzzy rejects values that are too different. "Melan" is three letters short of "Melanoma",
   * more than the one typo allowed for a five-letter term.
   */
  @Test
  public void testFuzzyRejectsDistantValues() {
    final EvsQuery query = new Fuzzy(TERMINOLOGIES, NAME_FIELD, "Melan");

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  /**
   * The number of allowed typos can be set explicitly, and a change of case counts as a typo.
   * "Melanomma" is one typo from "Melanoma" but two from "melanoma", so allowing exactly one typo
   * finds only "Melanoma".
   */
  @Test
  public void testFuzzyWithExplicitTypoLimit() {
    final EvsQuery query = new Fuzzy(TERMINOLOGIES, NAME_FIELD, "Melanomma", Fuzziness.ONE);

    assertEquals(Set.of("Melanoma"), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 5: CaseInsensitive wraps a query so that uppercase and lowercase letters match each
  // other. It works with Match, StartsWith, and Contains. Wrapping a Fuzzy query does not compile.
  // ---------------------------------------------------------------------------------------------

  /** A case-insensitive Match finds every value equal to the term, whatever its case. */
  @Test
  public void testCaseInsensitiveMatch() {
    final EvsQuery query =
        new CaseInsensitive(new Match(TERMINOLOGIES, NAME_FIELD, "MELANOMA"));

    assertEquals(Set.of("Melanoma", "melanoma"), fetchMatchingNames(query));
  }

  /** A case-insensitive StartsWith finds every value beginning with the term, whatever its case. */
  @Test
  public void testCaseInsensitiveStartsWith() {
    final EvsQuery query =
        new CaseInsensitive(new StartsWith(TERMINOLOGIES, NAME_FIELD, "melano"));

    assertEquals(Set.of("Melanoma", "melanoma", "Melanocyte"), fetchMatchingNames(query));
  }

  /** A case-insensitive Contains finds every value including the term, whatever its case. */
  @Test
  public void testCaseInsensitiveContains() {
    final EvsQuery query =
        new CaseInsensitive(new Contains(TERMINOLOGIES, NAME_FIELD, "MELANOMA"));

    assertEquals(Set.of("Melanoma", "melanoma", "Malignant Melanoma"), fetchMatchingNames(query));
  }

  /**
   * Wrapping a query does not change it. The original query is still case sensitive and can be
   * used on its own.
   */
  @Test
  public void testCaseInsensitiveLeavesWrappedQueryUnchanged() {
    final Match caseSensitiveQuery = new Match(TERMINOLOGIES, NAME_FIELD, "melanoma");
    final EvsQuery caseInsensitiveQuery = new CaseInsensitive(caseSensitiveQuery);

    assertEquals(Set.of("melanoma", "Melanoma"), fetchMatchingNames(caseInsensitiveQuery));
    assertEquals(Set.of("melanoma"), fetchMatchingNames(caseSensitiveQuery));
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
            .withQuery(query.getQueryBuilder())
            .withPageable(ALL_NAMES)
            .build();
    return operations.search(search, IndexedName.class, INDEX).stream()
        .map(SearchHit::getContent)
        .map(IndexedName::getName)
        .collect(Collectors.toSet());
  }

  /** A document in the tutorial index: one name stored as a whole, case-preserved value. */
  public static class IndexedName {

    /** The id, which is the name itself. */
    @Id private String id;

    /** The name. */
    @Field(type = FieldType.Keyword)
    private String name;

    /** Instantiates an empty {@link IndexedName}. */
    public IndexedName() {
      // n/a
    }

    /**
     * Instantiates an {@link IndexedName}.
     *
     * @param name the name
     */
    public IndexedName(final String name) {
      this.id = name;
      this.name = name;
    }

    /**
     * Returns the id.
     *
     * @return the id
     */
    public String getId() {
      return id;
    }

    /**
     * Sets the id.
     *
     * @param id the id
     */
    public void setId(final String id) {
      this.id = id;
    }

    /**
     * Returns the name.
     *
     * @return the name
     */
    public String getName() {
      return name;
    }

    /**
     * Sets the name.
     *
     * @param name the name
     */
    public void setName(final String name) {
      this.name = name;
    }
  }
}
