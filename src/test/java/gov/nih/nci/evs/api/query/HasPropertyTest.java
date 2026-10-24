package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Property;
import gov.nih.nci.evs.api.model.Qualifier;
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

  /** Property linking a gene to a Gene Ontology term. Its qualifiers say which term and why. */
  private static final String GO_ANNOTATION = "GO_Annotation";

  /** Code of {@link #GO_ANNOTATION}. */
  private static final String GO_ANNOTATION_CODE = "P211";

  /** Qualifier holding a GO annotation's Gene Ontology id. */
  private static final String GO_ID = "go-id";

  /** Code of {@link #GO_ID}. */
  private static final String GO_ID_CODE = "P387";

  /** Qualifier holding a GO annotation's evidence code. */
  private static final String GO_EVIDENCE = "go-evi";

  /** Code of {@link #GO_EVIDENCE}. */
  private static final String GO_EVIDENCE_CODE = "P389";

  /** A GO annotation value. */
  private static final String CELL_CYCLE_ARREST = "cell cycle arrest";

  /** Gene Ontology id of {@link #CELL_CYCLE_ARREST}. */
  private static final String CELL_CYCLE_ARREST_GO_ID = "GO:0007050";

  /** A GO annotation value. */
  private static final String ACTIVIN_INHIBITOR_ACTIVITY = "activin inhibitor activity";

  /** Gene Ontology id of {@link #ACTIVIN_INHIBITOR_ACTIVITY}. */
  private static final String ACTIVIN_INHIBITOR_ACTIVITY_GO_ID = "GO:0017106";

  /** Evidence code: traceable author statement. */
  private static final String TRACEABLE_AUTHOR_STATEMENT = "TAS";

  /** Evidence code: inferred from direct assay. */
  private static final String DIRECT_ASSAY = "IDA";

  /**
   * The concepts in the index. "Nevus" is Benign, but has a different property whose value is
   * "Malignant", which lesson 1 relies on. "Skin" has no properties. The two genes have GO
   * annotations: "INHBA Gene" has cell cycle arrest only from a direct assay, and an author
   * statement only on its other annotation, which lesson 5 relies on.
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
          getConcept("C04", "Skin"),
          getConcept(
              "C05",
              "INHBA Gene",
              getGoAnnotation(
                  ACTIVIN_INHIBITOR_ACTIVITY,
                  ACTIVIN_INHIBITOR_ACTIVITY_GO_ID,
                  TRACEABLE_AUTHOR_STATEMENT),
              getGoAnnotation(CELL_CYCLE_ARREST, CELL_CYCLE_ARREST_GO_ID, DIRECT_ASSAY)),
          getConcept(
              "C06",
              "TP53 Gene",
              getGoAnnotation(
                  CELL_CYCLE_ARREST, CELL_CYCLE_ARREST_GO_ID, TRACEABLE_AUTHOR_STATEMENT)));

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

  // ---------------------------------------------------------------------------------------------
  // Lesson 5: qualifiers must be on the same property.
  // ---------------------------------------------------------------------------------------------

  /**
   * Only "TP53 Gene" has cell cycle arrest from an author statement. "INHBA Gene" has cell cycle
   * arrest and an author statement, but on two different GO annotations, so it doesn't match.
   */
  @Test
  public void testQualifierMatchesOnSamePropertyEntry() {
    final EvsQuery genesWithAuthorStatedCellCycleArrest =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION),
            new Exact(CELL_CYCLE_ARREST),
            List.of(
                new PropertyQualifier(
                    new Exact(GO_EVIDENCE), new Exact(TRACEABLE_AUTHOR_STATEMENT))));

    assertEquals(Set.of("TP53 Gene"), fetchMatchingNames(genesWithAuthorStatedCellCycleArrest));
  }

  /**
   * Every qualifier must be on the same property, each on any of its qualifier entries. Both genes
   * have cell cycle arrest's GO id, but only "TP53 Gene" has it with an author statement.
   */
  @Test
  public void testAllQualifiersMatchOnSamePropertyEntry() {
    final EvsQuery genesWithAuthorStatedCellCycleArrestId =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION),
            List.of(
                new PropertyQualifier(new Exact(GO_ID), new Exact(CELL_CYCLE_ARREST_GO_ID)),
                new PropertyQualifier(
                    new Exact(GO_EVIDENCE), new Exact(TRACEABLE_AUTHOR_STATEMENT))));

    assertEquals(Set.of("TP53 Gene"), fetchMatchingNames(genesWithAuthorStatedCellCycleArrestId));
  }

  /** Without a property value, the qualifier alone picks the annotation. */
  @Test
  public void testQualifierWithoutPropertyValueFindsAnyValue() {
    final EvsQuery genesWithActivinInhibitorId =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION),
            List.of(
                new PropertyQualifier(
                    new Exact(GO_ID), new Exact(ACTIVIN_INHIBITOR_ACTIVITY_GO_ID))));

    assertEquals(Set.of("INHBA Gene"), fetchMatchingNames(genesWithActivinInhibitorId));
  }

  /** Without a qualifier value, any property with that qualifier type matches. */
  @Test
  public void testQualifierTypeWithoutValueFindsAnyValue() {
    final EvsQuery genesWithAnyEvidence =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION),
            List.of(new PropertyQualifier(new Exact(GO_EVIDENCE))));

    assertEquals(Set.of("INHBA Gene", "TP53 Gene"), fetchMatchingNames(genesWithAnyEvidence));
  }

  /**
   * A qualifier's type and value match on the same qualifier entry. Both genes have a go-id and a
   * "TAS" value, but no go-id is "TAS".
   */
  @Test
  public void testQualifierTypeAndValueMatchOnSameQualifierEntry() {
    final EvsQuery genesWithGoIdTas =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION),
            List.of(
                new PropertyQualifier(new Exact(GO_ID), new Exact(TRACEABLE_AUTHOR_STATEMENT))));

    assertEquals(Set.of(), fetchMatchingNames(genesWithGoIdTas));
  }

  /** The qualifier's code finds the same concepts as its name. */
  @Test
  public void testQualifierCodeMatchesLikeName() {
    final EvsQuery byName =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION),
            List.of(new PropertyQualifier(new Exact(GO_EVIDENCE), new Exact(DIRECT_ASSAY))));
    final EvsQuery byCode =
        new HasProperty(
            terminologies,
            new Exact(GO_ANNOTATION_CODE),
            List.of(new PropertyQualifier(new Exact(GO_EVIDENCE_CODE), new Exact(DIRECT_ASSAY))));

    assertEquals(Set.of("INHBA Gene"), fetchMatchingNames(byName));
    assertEquals(fetchMatchingNames(byName), fetchMatchingNames(byCode));
  }

  /** A property type, and a value or qualifiers when given, are required. */
  @Test
  public void testRejectsMissingValues() {
    final ValueMatch missingMatch = null;
    final List<PropertyQualifier> missingQualifiers = null;
    assertThrows(NullPointerException.class, () -> new HasProperty(terminologies, missingMatch));
    assertThrows(
        NullPointerException.class,
        () -> new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS), missingMatch));
    assertThrows(
        NullPointerException.class,
        () -> new HasProperty(terminologies, new Exact(NEOPLASTIC_STATUS), missingQualifiers));
    assertThrows(NullPointerException.class, () -> new PropertyQualifier(missingMatch));
    assertThrows(
        NullPointerException.class, () -> new PropertyQualifier(new Exact(GO_ID), missingMatch));
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

  /**
   * Builds a GO annotation property with its go-id and go-evi qualifiers.
   *
   * @param goTerm the Gene Ontology term's name, the property's value
   * @param goId the Gene Ontology term's id
   * @param evidenceCode how the annotation is supported
   * @return the property
   */
  private static Property getGoAnnotation(
      final String goTerm, final String goId, final String evidenceCode) {
    final Property property = getProperty(GO_ANNOTATION, GO_ANNOTATION_CODE, goTerm);
    property.setQualifiers(
        List.of(
            new Qualifier(GO_ID, goId, GO_ID_CODE),
            new Qualifier(GO_EVIDENCE, evidenceCode, GO_EVIDENCE_CODE)));
    return property;
  }
}
