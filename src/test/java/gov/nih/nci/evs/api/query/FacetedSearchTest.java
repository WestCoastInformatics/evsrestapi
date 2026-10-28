package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import gov.nih.nci.evs.api.model.Association;
import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.IncludeParam;
import gov.nih.nci.evs.api.model.Property;
import gov.nih.nci.evs.api.model.Role;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.model.TerminologyMetadata;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import gov.nih.nci.evs.api.util.ConceptUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.search.aggregations.AbstractAggregationBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Tests for {@link FacetedSearch} and the facets nodes expose, on a small index shaped like the
 * Explore sidebar example: concepts in a two-level hierarchy, each with a semantic type, a primary
 * site and subsets.
 *
 * <pre>
 * R Root
 * ├─ N Neoplasm
 * │  ├─ M Neoplasm by Morphology:  C1, C3, C5, C7 (retired)
 * │  └─ S Neoplasm by Site:        C2, C5   (C5 is under both)
 * └─ D Disease:                    C4
 * </pre>
 *
 * <p>The base query in every test leaves out retired concepts, so C7 never counts. Runs against
 * OpenSearch on port 9201, like {@link ValueMatchTest}.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FacetedSearchTest {

  /** A throwaway index used only by these tests. */
  private static final String INDEX_NAME = "evs_faceted_search_test";

  /** Terminology of every test concept. */
  private static final String TERMINOLOGY = "ncit";

  /** Version of the test concepts. */
  private static final String VERSION = "25.12e";

  /** One page large enough to hold every indexed concept. */
  private static final Pageable ALL_CONCEPTS = PageRequest.of(0, 100);

  /** Return only the minimal concept fields. */
  private static final IncludeParam MINIMAL = new IncludeParam("minimal");

  /** The property holding the semantic type. */
  private static final String SEMANTIC_TYPE = "Semantic_Type";

  /** Semantic type of the neoplasms. */
  private static final String NEOPLASTIC_PROCESS = "Neoplastic Process";

  /** Semantic type of the finding. */
  private static final String FINDING = "Finding";

  /** The primary site role's code. */
  private static final String PRIMARY_SITE = "R101";

  /** The primary site role's name. */
  private static final String PRIMARY_SITE_NAME = "Disease_Has_Primary_Anatomic_Site";

  /** Primary site: skin. */
  private static final String SKIN = "C12470";

  /** Primary site: eye. */
  private static final String EYE = "C12401";

  /** Primary site: mucosa. */
  private static final String MUCOSA = "C13166";

  /** The association marking subset membership. */
  private static final String CONCEPT_IN_SUBSET = "Concept_In_Subset";

  /** Code of {@link #CONCEPT_IN_SUBSET}. */
  private static final String CONCEPT_IN_SUBSET_CODE = "A8";

  /** A subset. */
  private static final String CTRP = "C116977";

  /** Another subset. */
  private static final String SDTM = "C66830";

  /** Status of retired concepts, which the base query leaves out. */
  private static final String RETIRED = "Retired_Concept";

  /** The field holding a concept's status. */
  private static final String CONCEPT_STATUS_FIELD = "conceptStatus";

  /** Name of the branch facet below the root. */
  private static final String ROOT_BRANCH_FACET = "branch:R";

  /** Name of the branch facet below Neoplasm. */
  private static final String NEOPLASM_BRANCH_FACET = "branch:N";

  /** Name of the semantic type facet. */
  private static final String SEMANTIC_TYPE_FACET = "properties:" + SEMANTIC_TYPE;

  /** Name of the primary site facet. */
  private static final String PRIMARY_SITE_FACET = "roles:" + PRIMARY_SITE;

  /** Name of the subset facet. */
  private static final String SUBSET_FACET = "subset";

  /** The concepts in the index. */
  private static final List<Concept> INDEXED_CONCEPTS =
      List.of(
          getHierarchyConcept("R", "Root", Map.of()),
          getHierarchyConcept("N", "Neoplasm", Map.of("R", 1)),
          getHierarchyConcept("D", "Disease", Map.of("R", 1)),
          getHierarchyConcept("M", "Neoplasm by Morphology", Map.of("N", 1, "R", 2)),
          getHierarchyConcept("S", "Neoplasm by Site", Map.of("N", 1, "R", 2)),
          getDisease(
              "C1",
              "Cutaneous Melanoma",
              Map.of("M", 1, "N", 2, "R", 3),
              NEOPLASTIC_PROCESS,
              SKIN,
              CTRP),
          getDisease(
              "C2",
              "Uveal Melanoma",
              Map.of("S", 1, "N", 2, "R", 3),
              NEOPLASTIC_PROCESS,
              EYE,
              CTRP,
              SDTM),
          getDisease(
              "C3",
              "Mucosal Melanoma",
              Map.of("M", 1, "N", 2, "R", 3),
              NEOPLASTIC_PROCESS,
              MUCOSA,
              SDTM),
          getDisease("C4", "Melanoma Finding", Map.of("D", 1, "R", 2), FINDING, SKIN),
          getDisease(
              "C5",
              "Acral Melanoma",
              Map.of("M", 1, "S", 1, "N", 2, "R", 3),
              NEOPLASTIC_PROCESS,
              SKIN,
              CTRP),
          getRetired(
              getDisease(
                  "C7",
                  "Retired Melanoma",
                  Map.of("M", 1, "N", 2, "R", 3),
                  NEOPLASTIC_PROCESS,
                  SKIN,
                  CTRP)));

  /** The project's OpenSearch wrapper, which selects the correct operations bean. */
  @Autowired private OpensearchOperationsService operationsService;

  /** The OpenSearch operations, taken from {@link #operationsService}. */
  private OpenSearchOperations operations;

  /** The terminology whose concept index is the test index. */
  private List<Terminology> terminologies;

  /** Creates the index, mapped like a concept index, and loads its concepts. */
  @BeforeAll
  public void setUpIndex() {
    operations = operationsService.getOpenSearchOperations();
    terminologies = List.of(getTerminology());
    createIndex();
  }

  /** Deletes the test index. */
  @AfterAll
  public void tearDownIndex() {
    operations.indexOps(IndexCoordinates.of(INDEX_NAME)).delete();
  }

  // ---------------------------------------------------------------------------------------------
  // Facets with nothing selected
  // ---------------------------------------------------------------------------------------------

  /** The branch facet counts each child of the anchor, with everything at or below it. */
  @Test
  public void testBranchFacetCountsEachChildWithEverythingBelowIt() {
    final FacetedSearchResult result = fetchFacets(List.of(Facet.of(getBranchDimension("R"))));

    assertEquals(Map.of("N", 7L, "D", 2L), result.facets().get(ROOT_BRANCH_FACET));
  }

  /** The property facet counts each value of its property type. */
  @Test
  public void testPropertyFacetCountsEachValueOfItsType() {
    final FacetedSearchResult result = fetchFacets(List.of(Facet.of(getSemanticTypeDimension())));

    assertEquals(
        Map.of(NEOPLASTIC_PROCESS, 4L, FINDING, 1L), result.facets().get(SEMANTIC_TYPE_FACET));
  }

  /** The role facet counts each target of its role type, per concept. */
  @Test
  public void testRoleFacetCountsEachTargetOfItsType() {
    final FacetedSearchResult result = fetchFacets(List.of(Facet.of(getPrimarySiteDimension())));

    assertEquals(Map.of(SKIN, 3L, EYE, 1L, MUCOSA, 1L), result.facets().get(PRIMARY_SITE_FACET));
  }

  /** The subset facet counts the members of every subset. */
  @Test
  public void testSubsetFacetCountsEachSubset() {
    final FacetedSearchResult result = fetchFacets(List.of(Facet.of(getSubsetDimension())));

    assertEquals(Map.of(CTRP, 3L, SDTM, 2L), result.facets().get(SUBSET_FACET));
  }

  /** Values come most concepts first, so the order is repeatable. */
  @Test
  public void testFacetValuesComeMostConceptsFirst() {
    final FacetedSearchResult result = fetchFacets(List.of(Facet.of(getPrimarySiteDimension())));

    assertEquals(
        List.of(SKIN, EYE, MUCOSA), List.copyOf(result.facets().get(PRIMARY_SITE_FACET).keySet()));
  }

  /** The base query applies to every facet: the retired C7 has Skin but isn't counted. */
  @Test
  public void testBaseQueryAppliesToEveryFacet() {
    final FacetedSearchResult result = fetchFacets(List.of(Facet.of(getPrimarySiteDimension())));

    assertEquals(3L, result.facets().get(PRIMARY_SITE_FACET).get(SKIN));
  }

  // ---------------------------------------------------------------------------------------------
  // Selections
  // ---------------------------------------------------------------------------------------------

  /** A selection narrows the hits: picking Skin returns only the Skin concepts. */
  @Test
  public void testSelectionNarrowsTheHits() {
    final FacetedSearchResult result =
        fetchFacets(List.of(Facet.of(getPrimarySiteDimension(), getPrimarySite(SKIN))));

    assertEquals(Set.of("C1", "C4", "C5"), getCodes(result));
  }

  /** The total counts the narrowed hits. */
  @Test
  public void testSelectionNarrowsTheTotal() {
    final FacetedSearchResult result =
        fetchFacets(List.of(Facet.of(getPrimarySiteDimension(), getPrimarySite(SKIN))));

    assertEquals(3L, result.total());
  }

  /** A facet ignores its own selection, so the other sites stay visible after picking Skin. */
  @Test
  public void testSelectedFacetStillShowsItsAlternatives() {
    final FacetedSearchResult result =
        fetchFacets(List.of(Facet.of(getPrimarySiteDimension(), getPrimarySite(SKIN))));

    assertEquals(Map.of(SKIN, 3L, EYE, 1L, MUCOSA, 1L), result.facets().get(PRIMARY_SITE_FACET));
  }

  /** Other facets apply the selection: after picking Skin, semantic types count only Skin ones. */
  @Test
  public void testOtherFacetsApplyTheSelection() {
    final FacetedSearchResult result =
        fetchFacets(
            List.of(
                Facet.of(getPrimarySiteDimension(), getPrimarySite(SKIN)),
                Facet.of(getSemanticTypeDimension())));

    assertEquals(
        Map.of(NEOPLASTIC_PROCESS, 2L, FINDING, 1L), result.facets().get(SEMANTIC_TYPE_FACET));
  }

  /** Several picks in one facet widen the hits: Skin or Eye. */
  @Test
  public void testPicksInOneFacetWidenTheHits() {
    final FacetedSearchResult result =
        fetchFacets(List.of(Facet.of(getPrimarySiteDimension(), getSkinOrEye())));

    assertEquals(Set.of("C1", "C2", "C4", "C5"), getCodes(result));
  }

  /** Facets combine as AND: Skin or Eye, and in CTRP. */
  @Test
  public void testFacetsCombineAsAnd() {
    final FacetedSearchResult result = fetchFacets(getSkinOrEyeInCtrpFacets());

    assertEquals(Set.of("C1", "C2", "C5"), getCodes(result));
  }

  /** The site facet counts within the other facet's pick (CTRP), not its own (Skin or Eye). */
  @Test
  public void testMultiSelectFacetCountsWithinOtherPicks() {
    final FacetedSearchResult result = fetchFacets(getSkinOrEyeInCtrpFacets());

    assertEquals(Map.of(SKIN, 2L, EYE, 1L), result.facets().get(PRIMARY_SITE_FACET));
  }

  /** The subset facet counts within the site picks, showing the subset the user could switch to. */
  @Test
  public void testSubsetFacetCountsWithinSitePicks() {
    final FacetedSearchResult result = fetchFacets(getSkinOrEyeInCtrpFacets());

    assertEquals(Map.of(CTRP, 3L, SDTM, 1L), result.facets().get(SUBSET_FACET));
  }

  /**
   * Drilling down: with Neoplasm picked in the root branch facet, a second branch facet below
   * Neoplasm counts its children. C5 is under both.
   */
  @Test
  public void testDrillDownCountsChildrenOfThePickedBranch() {
    final FacetedSearchResult result =
        fetchFacets(
            List.of(
                Facet.of(getBranchDimension("R"), new DescendantOrSelfOf(terminologies, "N")),
                Facet.of(getBranchDimension("N"))));

    assertEquals(Map.of("M", 4L, "S", 3L), result.facets().get(NEOPLASM_BRANCH_FACET));
  }

  // ---------------------------------------------------------------------------------------------
  // The facets nodes expose
  // ---------------------------------------------------------------------------------------------

  /** A branch facet is named after its anchors. */
  @Test
  public void testDescendantOfFacetIsNamedAfterItsAnchors() {
    assertEquals(List.of(ROOT_BRANCH_FACET), getFacetNames(getBranchDimension("R")));
  }

  /** A role facet is named after the role type. */
  @Test
  public void testHasRoleFacetIsNamedAfterItsType() {
    assertEquals(List.of(PRIMARY_SITE_FACET), getFacetNames(getPrimarySiteDimension()));
  }

  /** A property facet is named after the property type. */
  @Test
  public void testHasPropertyFacetIsNamedAfterItsType() {
    assertEquals(List.of(SEMANTIC_TYPE_FACET), getFacetNames(getSemanticTypeDimension()));
  }

  /** A field condition's facet is named after the field. */
  @Test
  public void testMatchAnyOfFacetIsNamedAfterItsField() {
    final EvsQuery retired = new MatchAnyOf(terminologies, CONCEPT_STATUS_FIELD, Set.of(RETIRED));

    assertEquals(List.of(CONCEPT_STATUS_FIELD), getFacetNames(retired));
  }

  /** And passes up its children's facets, each name once. */
  @Test
  public void testAndPassesUpChildFacetsOnce() {
    final EvsQuery query =
        new And(
            terminologies,
            List.of(
                getSemanticTypeDimension(), getSemanticTypeDimension(), getPrimarySiteDimension()));

    assertEquals(List.of(SEMANTIC_TYPE_FACET, PRIMARY_SITE_FACET), getFacetNames(query));
  }

  /** Not passes up its child's facet. */
  @Test
  public void testNotPassesUpChildFacet() {
    final EvsQuery query = new Not(terminologies, getSemanticTypeDimension());

    assertEquals(List.of(SEMANTIC_TYPE_FACET), getFacetNames(query));
  }

  /** Nodes without a natural facet, such as ancestor conditions, expose none. */
  @Test
  public void testAncestorOfHasNoFacet() {
    assertEquals(List.of(), getFacetNames(new AncestorOf(terminologies, "C1")));
  }

  // ---------------------------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------------------------

  /**
   * Runs a faceted search over every concept that isn't retired.
   *
   * @param facets the facets to count
   * @return the result
   */
  private FacetedSearchResult fetchFacets(final List<Facet> facets) {
    final EvsQuery notRetired =
        new Not(
            terminologies, new MatchAnyOf(terminologies, CONCEPT_STATUS_FIELD, Set.of(RETIRED)));
    return FacetedSearch.fetch(operations, notRetired, facets, ALL_CONCEPTS, MINIMAL);
  }

  /**
   * Returns the facet names a node exposes.
   *
   * @param query the node
   * @return the facet names, in order
   */
  private List<String> getFacetNames(final EvsQuery query) {
    return query.fetchFacetBuilders(operations).stream()
        .map(AbstractAggregationBuilder::getName)
        .toList();
  }

  /**
   * Returns the codes of the hits.
   *
   * @param result the result
   * @return the codes
   */
  private static Set<String> getCodes(final FacetedSearchResult result) {
    return result.concepts().stream().map(Concept::getCode).collect(Collectors.toSet());
  }

  /**
   * Returns the facets for "Skin or Eye, in CTRP".
   *
   * @return the facets
   */
  private List<Facet> getSkinOrEyeInCtrpFacets() {
    return List.of(
        Facet.of(getPrimarySiteDimension(), getSkinOrEye()),
        Facet.of(getSubsetDimension(), new InSubset(terminologies, CTRP)));
  }

  /**
   * Returns the pick "Skin or Eye" in the primary site facet.
   *
   * @return the selection
   */
  private EvsQuery getSkinOrEye() {
    return new Or(terminologies, List.of(getPrimarySite(SKIN), getPrimarySite(EYE)));
  }

  /**
   * Returns the branch facet's dimension below an anchor.
   *
   * @param anchorCode the anchor's code
   * @return the dimension
   */
  private EvsQuery getBranchDimension(final String anchorCode) {
    return new DescendantOf(terminologies, anchorCode);
  }

  /**
   * Returns the semantic type facet's dimension.
   *
   * @return the dimension
   */
  private EvsQuery getSemanticTypeDimension() {
    return new HasProperty(terminologies, new Exact(SEMANTIC_TYPE));
  }

  /**
   * Returns the primary site facet's dimension.
   *
   * @return the dimension
   */
  private EvsQuery getPrimarySiteDimension() {
    return new HasRole(terminologies, new RelationshipType(new Exact(PRIMARY_SITE)));
  }

  /**
   * Returns the subset facet's dimension. Its own subset doesn't matter: the facet counts all.
   *
   * @return the dimension
   */
  private EvsQuery getSubsetDimension() {
    return new InSubset(terminologies, CTRP);
  }

  /**
   * Returns the condition "primary site is this site".
   *
   * @param siteCode the site's code
   * @return the condition
   */
  private EvsQuery getPrimarySite(final String siteCode) {
    return new HasRole(
        terminologies,
        new RelationshipType(new Exact(PRIMARY_SITE)),
        new RelationshipTarget(new Exact(siteCode)));
  }

  /**
   * Builds the test terminology, with stored ancestor fields so hierarchy queries use them.
   *
   * @return the terminology
   */
  private static Terminology getTerminology() {
    final Terminology terminology =
        QueryTestTerminologies.getTerminology(TERMINOLOGY, VERSION, INDEX_NAME);
    final TerminologyMetadata metadata = new TerminologyMetadata();
    metadata.setHierarchy(true);
    metadata.setHierarchyFields(true);
    metadata.setSubsetMember(Set.of(CONCEPT_IN_SUBSET_CODE));
    terminology.setMetadata(metadata);
    return terminology;
  }

  /** Creates the index mapped like a concept index and loads the concepts into it. */
  private void createIndex() {
    final IndexCoordinates index = IndexCoordinates.of(INDEX_NAME);
    final IndexOperations indexOperations = operations.indexOps(index);
    indexOperations.delete();
    indexOperations.create();
    indexOperations.putMapping(Concept.class);
    operations.save(INDEXED_CONCEPTS, index);
    indexOperations.refresh();
  }

  /**
   * Builds a hierarchy concept with its ancestors and their shortest distances.
   *
   * @param code the code
   * @param name the name
   * @param depthsByAncestor each ancestor's shortest distance
   * @return the concept
   */
  private static Concept getHierarchyConcept(
      final String code, final String name, final Map<String, Integer> depthsByAncestor) {
    final Concept concept = new Concept();
    concept.setCode(code);
    concept.setName(name);
    concept.setNormName(ConceptUtils.normalize(name));
    concept.setTerminology(TERMINOLOGY);
    concept.setVersion(VERSION);
    concept.setAncestors(new ArrayList<>(depthsByAncestor.keySet()));
    concept.setAncestorDepths(
        depthsByAncestor.entrySet().stream()
            .map(depth -> depth.getKey() + DescendantOf.DEPTH_SEPARATOR + depth.getValue())
            .collect(Collectors.toList()));
    return concept;
  }

  /**
   * Builds a disease with a semantic type, a primary site and subsets.
   *
   * @param code the code
   * @param name the name
   * @param depthsByAncestor each ancestor's shortest distance
   * @param semanticType the semantic type
   * @param siteCode the primary site's code
   * @param subsetCodes the subsets it's in
   * @return the concept
   */
  private static Concept getDisease(
      final String code,
      final String name,
      final Map<String, Integer> depthsByAncestor,
      final String semanticType,
      final String siteCode,
      final String... subsetCodes) {
    final Concept concept = getHierarchyConcept(code, name, depthsByAncestor);
    concept.setProperties(List.of(getProperty(SEMANTIC_TYPE, semanticType)));
    concept.setRoles(List.of(getPrimarySiteRole(siteCode)));
    concept.setAssociations(
        List.of(subsetCodes).stream().map(FacetedSearchTest::getMembership).toList());
    return concept;
  }

  /**
   * Marks a concept retired.
   *
   * @param concept the concept
   * @return the same concept
   */
  private static Concept getRetired(final Concept concept) {
    concept.setConceptStatus(RETIRED);
    return concept;
  }

  /**
   * Builds a property.
   *
   * @param type the property type
   * @param value the value
   * @return the property
   */
  private static Property getProperty(final String type, final String value) {
    final Property property = new Property();
    property.setType(type);
    property.setValue(value);
    return property;
  }

  /**
   * Builds a primary site role.
   *
   * @param siteCode the site's code
   * @return the role
   */
  private static Role getPrimarySiteRole(final String siteCode) {
    final Role role = new Role();
    role.setType(PRIMARY_SITE_NAME);
    role.setCode(PRIMARY_SITE);
    role.setRelatedCode(siteCode);
    return role;
  }

  /**
   * Builds a membership in a subset.
   *
   * @param subsetCode the subset's code
   * @return the association
   */
  private static Association getMembership(final String subsetCode) {
    final Association association = new Association();
    association.setType(CONCEPT_IN_SUBSET);
    association.setCode(CONCEPT_IN_SUBSET_CODE);
    association.setRelatedCode(subsetCode);
    return association;
  }
}
