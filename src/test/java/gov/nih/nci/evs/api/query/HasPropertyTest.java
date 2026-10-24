package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Property;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import gov.nih.nci.evs.api.util.ConceptUtils;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A tutorial for {@link HasProperty}, which finds concepts by property type and value.
 *
 * <p>Every lesson follows the same steps:
 *
 * <ol>
 *   <li>A small index holds the concepts in {@link #INDEXED_CONCEPTS}, mapped exactly like a real
 *       concept index, so the nested properties mapping is tested too.
 *   <li>The lesson builds a query, e.g. {@code new HasProperty(terminologies, new
 *       Exact(NEOPLASTIC_STATUS), new Exact(MALIGNANT))}.
 *   <li>The query runs against the index, and the lesson checks which concept names came back.
 * </ol>
 *
 * <p>The property names and codes are NCIt's, but the concepts are made up for the tutorial.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HasPropertyTest {

  /** A throwaway index used only by this tutorial. */
  private static final String INDEX_NAME = "evs_has_property_tutorial";

  /** Terminology of every tutorial concept. */
  private static final String TERMINOLOGY = "ncit";

  /** Version of the tutorial concepts. */
  private static final String VERSION = "25.12e";

  /** One page large enough to hold every indexed concept. */
  private static final Pageable ALL_CONCEPTS = PageRequest.of(0, 100);

  /** Property saying whether a neoplasm is benign or malignant. */
  private static final String NEOPLASTIC_STATUS = "Neoplastic_Status";

  /** Code of {@link #NEOPLASTIC_STATUS}. */
  private static final String NEOPLASTIC_STATUS_CODE = "P363";

  /** Property holding a concept's ICD-O-3 code. */
  private static final String ICD_O_3_CODE = "ICD-O-3_Code";

  /** Code of {@link #ICD_O_3_CODE}. */
  private static final String ICD_O_3_CODE_CODE = "P334";

  /** Property holding a concept's name in older NCIt versions. */
  private static final String LEGACY_CONCEPT_NAME = "Legacy Concept Name";

  /** Code of {@link #LEGACY_CONCEPT_NAME}. */
  private static final String LEGACY_CONCEPT_NAME_CODE = "P366";

  /** A neoplastic status value. */
  private static final String MALIGNANT = "Malignant";

  /** A neoplastic status value. */
  private static final String BENIGN = "Benign";

  /**
   * The concepts in the index. "Nevus" is Benign, but has a different property whose value is
   * "Malignant", which lesson 1 relies on. "Skin" has no properties.
   */
  private static final List<Concept> INDEXED_CONCEPTS =
      List.of(
          getConcept(
              "C01",
              "Melanoma",
              getProperty(NEOPLASTIC_STATUS, NEOPLASTIC_STATUS_CODE, MALIGNANT),
              getProperty(ICD_O_3_CODE, ICD_O_3_CODE_CODE, "8720/3")),
          getConcept(
              "C02",
              "Nevus",
              getProperty(NEOPLASTIC_STATUS, NEOPLASTIC_STATUS_CODE, BENIGN),
              getProperty(LEGACY_CONCEPT_NAME, LEGACY_CONCEPT_NAME_CODE, MALIGNANT)),
          getConcept(
              "C03",
              "Basal Cell Carcinoma",
              getProperty(NEOPLASTIC_STATUS, NEOPLASTIC_STATUS_CODE, MALIGNANT),
              getProperty(ICD_O_3_CODE, ICD_O_3_CODE_CODE, "8090/3")),
          getConcept("C04", "Skin"));

  /** The project's OpenSearch wrapper, which selects the correct operations bean. */
  @Autowired private OpensearchOperationsService operationsService;

  /** The OpenSearch operations, taken from {@link #operationsService}. */
  private OpenSearchOperations operations;

  /** The terminology whose concept index is the tutorial index. */
  private List<Terminology> terminologies;

  /** Creates the index, mapped like a concept index, and loads its concepts. */
  @BeforeAll
  public void setUpIndex() {
    operations = operationsService.getOpenSearchOperations();
    createIndex();

    terminologies =
        List.of(QueryTestTerminologies.getTerminology(TERMINOLOGY, VERSION, INDEX_NAME));
  }

  /** Removes the index. */
  @AfterAll
  public void tearDownIndex() {
    operations.indexOps(IndexCoordinates.of(INDEX_NAME)).delete();
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 1: HasProperty finds concepts with a property of the given type and value.
  // ---------------------------------------------------------------------------------------------

  /**
   * Both malignant concepts are found. "Nevus" has a neoplastic status and a value "Malignant", but
   * on two different properties, so it doesn't match.
   */
  @Test
  public void testPropertyTypeAndValueMatchOnSameEntry() {
    final EvsQuery malignantConcepts =
        new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS), new Exact(MALIGNANT));

    assertEquals(Set.of("Melanoma", "Basal Cell Carcinoma"), fetchMatchingNames(malignantConcepts));
  }

  /** The value only matches under its own type: no ICD-O-3 code is "Malignant". */
  @Test
  public void testValueUnderOtherTypeFindsNothing() {
    final EvsQuery conceptsWithIcdO3CodeMalignant =
        new HasProperty(terminologies, new Exact(ICD_O_3_CODE), new Exact(MALIGNANT));

    assertEquals(Set.of(), fetchMatchingNames(conceptsWithIcdO3CodeMalignant));
  }

  /** Nothing has a property type that isn't indexed. */
  @Test
  public void testUnmatchedPropertyTypeFindsNothing() {
    final EvsQuery conceptsWithSemanticType = new HasProperty(terminologies, new Exact("P106"));

    assertEquals(Set.of(), fetchMatchingNames(conceptsWithSemanticType));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: the value is optional.
  // ---------------------------------------------------------------------------------------------

  /** Without a value, every concept with a neoplastic status is found, whatever its value. */
  @Test
  public void testPropertyTypeWithoutValueFindsAnyValue() {
    final EvsQuery conceptsWithAnyNeoplasticStatus =
        new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS));

    assertEquals(
        Set.of("Melanoma", "Nevus", "Basal Cell Carcinoma"),
        fetchMatchingNames(conceptsWithAnyNeoplasticStatus));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: a property type is a name or a code.
  // ---------------------------------------------------------------------------------------------

  /** The property's code finds the same concepts as its name. */
  @Test
  public void testPropertyCodeMatchesLikeName() {
    final EvsQuery byName =
        new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS), new Exact(MALIGNANT));
    final EvsQuery byCode =
        new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS_CODE), new Exact(MALIGNANT));

    assertEquals(fetchMatchingNames(byName), fetchMatchingNames(byCode));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: the value takes any value match.
  // ---------------------------------------------------------------------------------------------

  /** Values are case sensitive by default. */
  @Test
  public void testPropertyValueIsCaseSensitive() {
    final EvsQuery conceptsWithLowercaseMalignantStatus =
        new HasProperty(
            terminologies, new Exact(NEOPLASTIC_STATUS), new Exact(MALIGNANT.toLowerCase()));

    assertEquals(Set.of(), fetchMatchingNames(conceptsWithLowercaseMalignantStatus));
  }

  /** With its match wrapped in CaseInsensitive, the value matches whatever its case. */
  @Test
  public void testCaseInsensitivePropertyValueMatchesAnyCase() {
    final EvsQuery conceptsWithAnyCaseMalignantStatus =
        new HasProperty(
            terminologies,
            new Exact(NEOPLASTIC_STATUS),
            new CaseInsensitive(new Exact(MALIGNANT.toLowerCase())));

    assertEquals(
        Set.of("Melanoma", "Basal Cell Carcinoma"),
        fetchMatchingNames(conceptsWithAnyCaseMalignantStatus));
  }

  /** ICD-O-3 codes starting with 8720 are melanomas. */
  @Test
  public void testPropertyValueMatchedByStartsWith() {
    final EvsQuery conceptsWithMelanomaMorphology =
        new HasProperty(terminologies, new Exact(ICD_O_3_CODE_CODE), new StartsWith("8720"));

    assertEquals(Set.of("Melanoma"), fetchMatchingNames(conceptsWithMelanomaMorphology));
  }

  /** A property type and, when given, a value are required. */
  @Test
  public void testRejectsMissingValues() {
    assertThrows(NullPointerException.class, () -> new HasProperty(terminologies, null));
    assertThrows(
        NullPointerException.class,
        () -> new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS), null));
  }

  /**
   * Runs the query against the index it searches.
   *
   * @param query the query to run
   * @return the names of the matching concepts
   */
  private Set<String> fetchMatchingNames(final EvsQuery query) {
    final NativeSearchQuery search =
        new NativeSearchQueryBuilder()
            .withQuery(query.fetchOpenSearchQuery(operations))
            .withPageable(ALL_CONCEPTS)
            .build();
    return operations.search(search, Concept.class, query.getIndexCoordinates()).stream()
        .map(SearchHit::getContent)
        .map(Concept::getName)
        .collect(Collectors.toSet());
  }

  /** Creates the index mapped like a concept index and loads the concepts into it. */
  private void createIndex() {
    for (final Concept concept : INDEXED_CONCEPTS) {
      concept.setTerminology(TERMINOLOGY);
      concept.setVersion(VERSION);
    }
    final IndexCoordinates index = IndexCoordinates.of(INDEX_NAME);
    final IndexOperations indexOperations = operations.indexOps(index);
    indexOperations.delete();
    indexOperations.create();
    indexOperations.putMapping(Concept.class);
    operations.save(INDEXED_CONCEPTS, index);
    indexOperations.refresh();
  }

  /**
   * Builds a concept with a normalized name and the given properties.
   *
   * @param code the concept code
   * @param name the preferred name
   * @param properties the concept's properties
   * @return the concept
   */
  private static Concept getConcept(
      final String code, final String name, final Property... properties) {
    final Concept concept = new Concept();
    concept.setCode(code);
    concept.setName(name);
    concept.setNormName(ConceptUtils.normalize(name));
    concept.setProperties(List.of(properties));
    return concept;
  }

  /**
   * Builds a property.
   *
   * @param type the property's name
   * @param code the property's code
   * @param value the property's value
   * @return the property
   */
  private static Property getProperty(final String type, final String code, final String value) {
    final Property property = new Property(type, value);
    property.setCode(code);
    return property;
  }
}
