package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Role;
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
import org.opensearch.index.query.QueryBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A tutorial for {@link HasRole}, which finds concepts by role type and role target.
 *
 * <p>Every lesson follows the same steps:
 *
 * <ol>
 *   <li>A small index holds the concepts in {@link #INDEXED_CONCEPTS}, mapped exactly like a real
 *       concept index, so the nested roles mapping is tested too.
 *   <li>The lesson builds a query, e.g. {@code getHasRole(PRIMARY_SITE, SKIN)}.
 *   <li>The query runs against the index, and the lesson checks which concept names came back.
 * </ol>
 *
 * <p>A role target is either a value match, compared against the role's target name or code, or a
 * query over other concepts, which runs before the role query. The query targets here are other
 * {@link HasRole}s, e.g. "concepts that are a complication of a disease whose primary site is
 * Skin".
 *
 * <p>The role names and codes are made up for the tutorial. They look like NCIt's but don't need to
 * match it.
 *
 * <p>TODO: add full integration tests against a real NCIt concept index once it's reindexed with
 * nested roles (local {@code concept_ncit_2512e} still has flattened roles, so {@link HasRole}
 * fails on it). Check the counts from the 2026-10-20 benchmark: name contains "cutaneous melanoma"
 * and primary site at or below C12219 finds 64; below Neoplasm (C3262) with primary site at or
 * below C12219 finds 13,335. The C3262 branch has 15,233 descendants, so a target over it also
 * exercises paging past one page of codes against a live index. Paging and the code limit are unit
 * tested with mocks in {@link DeclareDependenciesTest}.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HasRoleTest {

  /** A throwaway index used only by this tutorial. */
  private static final String INDEX_NAME = "evs_has_role_tutorial";

  /**
   * A second throwaway index, standing in for the next version of the same terminology. It reuses
   * Skin's code for a concept named "Integument", so codes alone can't tell the versions apart.
   */
  private static final String NEXT_VERSION_INDEX_NAME = "evs_has_role_tutorial_next_version";

  /** Terminology of every tutorial concept. */
  private static final String TERMINOLOGY = "ncit";

  /** Version of the concepts in the tutorial index. */
  private static final String VERSION = "25.12e";

  /** Version of the concepts in {@link #NEXT_VERSION_INDEX_NAME}. */
  private static final String NEXT_VERSION = "26.01a";

  /** One page large enough to hold every indexed concept. */
  private static final Pageable ALL_CONCEPTS = PageRequest.of(0, 100);

  /** Role linking a disease to where it primarily occurs. */
  private static final String PRIMARY_SITE = "Disease_Has_Primary_Anatomic_Site";

  /** Code of {@link #PRIMARY_SITE}. */
  private static final String PRIMARY_SITE_CODE = "R101";

  /** Role linking a disease to a site it is associated with. */
  private static final String ASSOCIATED_SITE = "Disease_Has_Associated_Anatomic_Site";

  /** Code of {@link #ASSOCIATED_SITE}. */
  private static final String ASSOCIATED_SITE_CODE = "R100";

  /** Target concept: Skin. */
  private static final String SKIN = "C12470";

  /** Target concept: Lung. */
  private static final String LUNG = "C12468";

  /** Target concept: Liver. No indexed concept points to it. */
  private static final String LIVER = "C12392";

  /** A code prefix shared by {@link #SKIN} and {@link #LUNG}, but not {@link #LIVER}. */
  private static final String SKIN_AND_LUNG_CODE_PREFIX = "C124";

  /** Made-up role linking a concept to a disease it's a complication of. */
  private static final String COMPLICATION_OF = "Complication_Of_Disease";

  /** Code of {@link #COMPLICATION_OF}. */
  private static final String COMPLICATION_OF_CODE = "R999";

  /** Made-up role linking an anatomic structure to the structure it is part of. */
  private static final String PART_OF = "Anatomic_Structure_Is_Physical_Part_Of";

  /** Code of {@link #PART_OF}. */
  private static final String PART_OF_CODE = "R998";

  /** Made-up structure that Skin is part of. Not indexed: only Skin's role points to it. */
  private static final String BODY_SURFACE = "C99001";

  /**
   * The concepts in the index. "Lung Carcinoma" has both sites, but Skin only through the
   * associated-site role, which lesson 1 relies on. "Lung Adenocarcinoma" has only a Lung site.
   * Skin, Lung, and Liver are role targets. "Skin Ulcer" is a complication of a disease, and Skin
   * is part of Body Surface, which lesson 2b's nested targets rely on.
   */
  private static final List<Concept> INDEXED_CONCEPTS =
      List.of(
          getConcept(
              "C01", "Cutaneous Melanoma", getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN, "Skin")),
          getConcept(
              "C02",
              "Lung Carcinoma",
              getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, LUNG, "Lung"),
              getRole(ASSOCIATED_SITE, ASSOCIATED_SITE_CODE, SKIN, "Skin")),
          getConcept(
              "C03",
              "Basal Cell Carcinoma of Skin",
              getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN, "Skin")),
          getConcept("C04", "Melanoma"),
          getConcept(
              "C07", "Lung Adenocarcinoma", getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, LUNG, "Lung")),
          getConcept(
              "C05",
              "Skin Ulcer",
              getRole(COMPLICATION_OF, COMPLICATION_OF_CODE, "C01", "Cutaneous Melanoma")),
          getConcept(SKIN, "Skin", getRole(PART_OF, PART_OF_CODE, BODY_SURFACE, "Body Surface")),
          getConcept(LUNG, "Lung"),
          getConcept(LIVER, "Liver"));

  /**
   * The concepts in {@link #NEXT_VERSION_INDEX_NAME}. Skin's code now names "Integument", and
   * Cutaneous Melanoma's code C01 now names "Melanoma of Integument", whose primary site is
   * Integument. "Integument Ulcer" is a complication of it, reusing Skin Ulcer's code C05.
   */
  private static final List<Concept> NEXT_VERSION_CONCEPTS =
      List.of(
          getConcept(SKIN, "Integument"),
          getConcept(
              "C01",
              "Melanoma of Integument",
              getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN, "Integument")),
          getConcept(
              "C05",
              "Integument Ulcer",
              getRole(COMPLICATION_OF, COMPLICATION_OF_CODE, "C01", "Melanoma of Integument")));

  /** The project's OpenSearch wrapper, which selects the correct operations bean. */
  @Autowired private OpensearchOperationsService operationsService;

  /** The OpenSearch operations, taken from {@link #operationsService}. */
  private OpenSearchOperations operations;

  /** The terminology whose concept index is the tutorial index. */
  private List<Terminology> terminologies;

  /** Both versions: the tutorial index and {@link #NEXT_VERSION_INDEX_NAME}. */
  private List<Terminology> bothVersions;

  /** Creates both indexes, mapped like concept indexes, and loads their concepts. */
  @BeforeAll
  public void setUpIndexes() {
    operations = operationsService.getOpenSearchOperations();
    createIndex(INDEX_NAME, VERSION, INDEXED_CONCEPTS);
    createIndex(NEXT_VERSION_INDEX_NAME, NEXT_VERSION, NEXT_VERSION_CONCEPTS);

    final Terminology currentVersion =
        QueryTestTerminologies.getTerminology(TERMINOLOGY, VERSION, INDEX_NAME);
    final Terminology nextVersion =
        QueryTestTerminologies.getTerminology(TERMINOLOGY, NEXT_VERSION, NEXT_VERSION_INDEX_NAME);
    terminologies = List.of(currentVersion);
    bothVersions = List.of(currentVersion, nextVersion);
  }

  /** Removes both indexes. */
  @AfterAll
  public void tearDownIndexes() {
    operations.indexOps(IndexCoordinates.of(INDEX_NAME)).delete();
    operations.indexOps(IndexCoordinates.of(NEXT_VERSION_INDEX_NAME)).delete();
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 1: HasRole finds concepts with a role of the given type pointing to the given target.
  // ---------------------------------------------------------------------------------------------

  /**
   * Both concepts whose primary site is Skin are found. "Lung Carcinoma" has a primary site and
   * points to Skin, but on two different roles, so it doesn't match.
   */
  @Test
  public void testRoleTypeAndTargetMatchOnSameRoleEntry() {
    final EvsQuery diseasesWithPrimarySiteSkin = getHasRole(PRIMARY_SITE, SKIN);

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithPrimarySiteSkin));
  }

  /** Nothing has a primary site of Liver. */
  @Test
  public void testUnmatchedTargetFindsNothing() {
    final EvsQuery diseasesWithPrimarySiteLiver = getHasRole(PRIMARY_SITE, LIVER);

    assertEquals(Set.of(), fetchMatchingNames(diseasesWithPrimarySiteLiver));
  }

  /** The associated-site role reaches Skin only from "Lung Carcinoma". */
  @Test
  public void testOtherRoleTypeFindsItsOwnTargets() {
    final EvsQuery diseasesWithAssociatedSiteSkin = getHasRole(ASSOCIATED_SITE, SKIN);

    assertEquals(Set.of("Lung Carcinoma"), fetchMatchingNames(diseasesWithAssociatedSiteSkin));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: the target is optional.
  // ---------------------------------------------------------------------------------------------

  /** Without a target, every concept with a primary site is found, whatever it points to. */
  @Test
  public void testRoleTypeWithoutTargetFindsAnyTarget() {
    final EvsQuery diseasesWithAnyPrimarySite =
        new HasRole(terminologies, new RelationshipType(new Exact(PRIMARY_SITE)));

    assertEquals(
        Set.of(
            "Cutaneous Melanoma",
            "Lung Carcinoma",
            "Basal Cell Carcinoma of Skin",
            "Lung Adenocarcinoma"),
        fetchMatchingNames(diseasesWithAnyPrimarySite));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2b: a target is a query, run before the role query.
  // ---------------------------------------------------------------------------------------------

  /**
   * A target query can match several concepts, and a role pointing to any of them matches. The
   * target query runs first: diseases whose primary site is Skin (Cutaneous Melanoma, Basal Cell
   * Carcinoma of Skin). Then the outer query: concepts that are a complication of one of them (Skin
   * Ulcer). A target query can have a query target of its own, to any depth.
   */
  @Test
  public void testTargetCanHaveItsOwnRoleTarget() {
    final EvsQuery diseasesWithPrimarySiteSkin = getHasRole(PRIMARY_SITE, SKIN);
    final EvsQuery complicationsOfDiseasesWithPrimarySiteSkin =
        new HasRole(
            terminologies,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(diseasesWithPrimarySiteSkin));

    assertEquals(
        Set.of("Skin Ulcer"), fetchMatchingNames(complicationsOfDiseasesWithPrimarySiteSkin));
  }

  /**
   * A target query matching no concepts finds nothing, rather than matching any target. Nothing has
   * a primary site of Liver, so nothing is a complication of such a disease.
   */
  @Test
  public void testTargetMatchingNothingFindsNothing() {
    final EvsQuery diseasesWithPrimarySiteLiver = getHasRole(PRIMARY_SITE, LIVER);
    final EvsQuery complicationsOfDiseasesWithPrimarySiteLiver =
        new HasRole(
            terminologies,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(diseasesWithPrimarySiteLiver));

    assertEquals(Set.of(), fetchMatchingNames(complicationsOfDiseasesWithPrimarySiteLiver));
  }

  /**
   * A type matching several role types works with a query target too. The target query runs once
   * and finds Skin, the only part of Body Surface. Then any role whose type contains "site",
   * primary or associated, matches if that same role points to Skin. "Lung Carcinoma" matches
   * through its associated site Skin. "Lung Adenocarcinoma" has a site, but only Lung, which isn't
   * part of Body Surface, so it doesn't match.
   */
  @Test
  public void testContainsTypeWithQueryTargetMatchesAnyRoleTypePointingToTarget() {
    final EvsQuery partsOfBodySurface = getHasRole(PART_OF, BODY_SURFACE);
    final EvsQuery diseasesWithAnySiteOnBodySurface =
        new HasRole(
            terminologies,
            new RelationshipType(new CaseInsensitive(new Contains("site"))),
            new RelationshipTarget(partsOfBodySurface));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Lung Carcinoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithAnySiteOnBodySurface));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2c: a target only matches roles in the terminology version it was found in.
  // ---------------------------------------------------------------------------------------------

  /**
   * Searching both versions, the target "diseases whose primary site is named Skin" is only found
   * in the first version: Cutaneous Melanoma (C01). The next version's Integument Ulcer is a
   * complication of the same code C01, but there that code names a melanoma of Integument, so it
   * doesn't match.
   */
  @Test
  public void testTargetFoundInOneVersionOnlyMatchesRolesInThatVersion() {
    final EvsQuery diseasesOfSiteNamedSkin =
        new HasRole(
            bothVersions,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(new CaseInsensitive(new Exact("Skin"))));
    final EvsQuery complicationsOfDiseasesOfSiteNamedSkin =
        new HasRole(
            bothVersions,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(diseasesOfSiteNamedSkin));

    assertEquals(Set.of("Skin Ulcer"), fetchMatchingNames(complicationsOfDiseasesOfSiteNamedSkin));
  }

  /**
   * The target "diseases whose primary site is Skin's code" is found in both versions, C01 in each,
   * so it matches the complication in both.
   */
  @Test
  public void testTargetFoundInBothVersionsMatchesRolesInBoth() {
    final EvsQuery diseasesOfSkinCode =
        new HasRole(
            bothVersions,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(new Exact(SKIN)));
    final EvsQuery complicationsOfDiseasesOfSkinCode =
        new HasRole(
            bothVersions,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(diseasesOfSkinCode));

    assertEquals(
        Set.of("Skin Ulcer", "Integument Ulcer"),
        fetchMatchingNames(complicationsOfDiseasesOfSkinCode));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: a role type is an exact name or code.
  // ---------------------------------------------------------------------------------------------

  /** The role's code finds the same concepts as its name. */
  @Test
  public void testRoleCodeMatchesLikeName() {
    final EvsQuery byName = getHasRole(PRIMARY_SITE, SKIN);
    final EvsQuery byCode = getHasRole(PRIMARY_SITE_CODE, SKIN);

    assertEquals(fetchMatchingNames(byName), fetchMatchingNames(byCode));
  }

  /** Part of a role name is not a role name. */
  @Test
  public void testPartialRoleNameFindsNothing() {
    final EvsQuery conceptsWithRoleNamedAnatomicSite =
        new HasRole(terminologies, new RelationshipType(new Exact("Anatomic_Site")));

    assertEquals(Set.of(), fetchMatchingNames(conceptsWithRoleNamedAnatomicSite));
  }

  /** Role types are case sensitive by default. */
  @Test
  public void testRoleTypeIsCaseSensitive() {
    final EvsQuery diseasesWithLowercasePrimarySiteSkin =
        getHasRole(PRIMARY_SITE.toLowerCase(), SKIN);

    assertEquals(Set.of(), fetchMatchingNames(diseasesWithLowercasePrimarySiteSkin));
  }

  /** With its match wrapped in CaseInsensitive, the role type matches whatever its case. */
  @Test
  public void testCaseInsensitiveRoleTypeMatchesAnyCase() {
    final RelationshipType roleType =
        new RelationshipType(new CaseInsensitive(new Exact(PRIMARY_SITE.toLowerCase())));
    final EvsQuery diseasesWithAnyCasePrimarySiteSkin = getHasRole(roleType, SKIN);

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithAnyCasePrimarySiteSkin));
  }

  /** CaseInsensitive also works on a role code. */
  @Test
  public void testCaseInsensitiveRoleCode() {
    final RelationshipType roleType =
        new RelationshipType(new CaseInsensitive(new Exact(PRIMARY_SITE_CODE.toLowerCase())));
    final EvsQuery diseasesWithAnyCasePrimarySiteCodeSkin = getHasRole(roleType, SKIN);

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithAnyCasePrimarySiteCodeSkin));
  }

  /**
   * A role type takes any value match, not just an exact name. "Anatomic_Site" is in both the
   * primary and the associated site role names, so "Lung Carcinoma" matches through its associated
   * site Skin.
   */
  @Test
  public void testRoleTypeMatchedByContains() {
    final RelationshipType roleType = new RelationshipType(new Contains("Anatomic_Site"));
    final EvsQuery diseasesWithAnyAnatomicSiteSkin = getHasRole(roleType, SKIN);

    assertEquals(
        Set.of("Cutaneous Melanoma", "Lung Carcinoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithAnyAnatomicSiteSkin));
  }

  /**
   * A target given as a match is compared against the role's target name, with no search for the
   * target first. "skinnn" is two edits from Skin. "Lung Carcinoma" points to Skin only through its
   * associated site, so it doesn't match a primary site.
   */
  @Test
  public void testTargetMatchedByFuzzyName() {
    final RelationshipType roleType = new RelationshipType(new Exact(PRIMARY_SITE));
    final EvsQuery diseasesWithPrimarySiteNamedLikeSkinnn =
        new HasRole(terminologies, roleType, new RelationshipTarget(new Fuzzy("skinnn")));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithPrimarySiteNamedLikeSkinnn));
  }

  /** A target given as a match is also compared against the role's target code. */
  @Test
  public void testTargetMatchedByCode() {
    final RelationshipType roleType = new RelationshipType(new Exact(PRIMARY_SITE));
    final EvsQuery diseasesWithPrimarySiteCodeSkin =
        new HasRole(terminologies, roleType, new RelationshipTarget(new Exact(SKIN)));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithPrimarySiteCodeSkin));
  }

  /**
   * A target match can match several targets. Every code starting with {@link
   * #SKIN_AND_LUNG_CODE_PREFIX} is Skin's or Lung's, so every concept whose primary site is either
   * one is found.
   */
  @Test
  public void testTargetMatchedByCodePrefixFindsAnyOfThem() {
    final RelationshipType roleType = new RelationshipType(new Exact(PRIMARY_SITE));
    final EvsQuery diseasesWithPrimarySiteSkinOrLung =
        new HasRole(
            terminologies,
            roleType,
            new RelationshipTarget(new StartsWith(SKIN_AND_LUNG_CODE_PREFIX)));

    assertEquals(
        Set.of(
            "Cutaneous Melanoma",
            "Lung Carcinoma",
            "Basal Cell Carcinoma of Skin",
            "Lung Adenocarcinoma"),
        fetchMatchingNames(diseasesWithPrimarySiteSkinOrLung));
  }

  /**
   * The type and the target can each be matched in any fashion: any role with "site" in its name,
   * whatever the case, pointing to a target with "skin" in its name. Both must hold on the same
   * role, so "Lung Carcinoma" matches through its associated site Skin, not its primary site Lung.
   */
  @Test
  public void testTypeAndTargetMatchedByCaseInsensitiveContains() {
    final EvsQuery conceptsWithSiteRoleTargetingSkin =
        new HasRole(
            terminologies,
            new RelationshipType(new CaseInsensitive(new Contains("site"))),
            new RelationshipTarget(new CaseInsensitive(new Contains("skin"))));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Lung Carcinoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(conceptsWithSiteRoleTargetingSkin));
  }

  /** A target given as a match that matches neither a target name nor a code finds nothing. */
  @Test
  public void testUnmatchedTargetNameOrCodeFindsNothing() {
    final RelationshipType roleType = new RelationshipType(new Exact(PRIMARY_SITE));
    final EvsQuery diseasesWithPrimarySiteNamedExactlyLiver =
        new HasRole(terminologies, roleType, new RelationshipTarget(new Exact("Liver")));

    assertEquals(Set.of(), fetchMatchingNames(diseasesWithPrimarySiteNamedExactlyLiver));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: HasRole combines with other queries.
  // ---------------------------------------------------------------------------------------------

  /**
   * Primary site Lung, and associated site Skin. Unlike lesson 1, the two conditions are on
   * different roles of the same concept, combined with And.
   */
  @Test
  public void testCombinesWithAnotherHasRole() {
    final EvsQuery hasLungPrimarySite = getHasRole(PRIMARY_SITE, LUNG);
    final EvsQuery hasSkinAssociatedSite = getHasRole(ASSOCIATED_SITE, SKIN);
    final EvsQuery lungDiseasesWithSkinAssociatedSite =
        new And(terminologies, List.of(hasLungPrimarySite, hasSkinAssociatedSite));

    assertEquals(Set.of("Lung Carcinoma"), fetchMatchingNames(lungDiseasesWithSkinAssociatedSite));
  }

  /** A role type and a target are required. */
  @Test
  public void testRejectsMissingValues() {
    assertThrows(NullPointerException.class, () -> new RelationshipType(null));
    final EvsQuery missingQuery = null;
    final ValueMatch missingMatch = null;
    assertThrows(NullPointerException.class, () -> new RelationshipTarget(missingQuery));
    assertThrows(NullPointerException.class, () -> new RelationshipTarget(missingMatch));
    assertThrows(NullPointerException.class, () -> new HasRole(terminologies, null));
  }

  /**
   * Builds a {@link HasRole} whose target is one concept, given by its exact code.
   *
   * @param roleNameOrCode the role's exact name or code
   * @param targetCode the code of the concept the role must point to
   * @return the query
   */
  private HasRole getHasRole(final String roleNameOrCode, final String targetCode) {
    return getHasRole(new RelationshipType(new Exact(roleNameOrCode)), targetCode);
  }

  /**
   * Builds a {@link HasRole} whose target is one concept, given by its exact code.
   *
   * @param roleType the role's type
   * @param targetCode the code of the concept the role must point to
   * @return the query
   */
  private HasRole getHasRole(final RelationshipType roleType, final String targetCode) {
    return new HasRole(terminologies, roleType, new RelationshipTarget(new Exact(targetCode)));
  }

  /**
   * Runs the query against the indexes it searches.
   *
   * @param query the query to run
   * @return the names of the matching concepts
   */
  private Set<String> fetchMatchingNames(final EvsQuery query) {
    return fetchMatchingNames(query.fetchOpenSearchQuery(operations), query.getIndexCoordinates());
  }

  /**
   * Runs the OpenSearch query against the given indexes.
   *
   * @param query the query to run
   * @param indexes the indexes to search
   * @return the names of the matching concepts
   */
  private Set<String> fetchMatchingNames(final QueryBuilder query, final IndexCoordinates indexes) {
    final NativeSearchQuery search =
        new NativeSearchQueryBuilder().withQuery(query).withPageable(ALL_CONCEPTS).build();
    return operations.search(search, Concept.class, indexes).stream()
        .map(SearchHit::getContent)
        .map(Concept::getName)
        .collect(Collectors.toSet());
  }

  /**
   * Creates an index mapped like a concept index and loads the concepts into it, as one version of
   * the tutorial terminology.
   *
   * @param indexName the index name
   * @param version the version the concepts belong to
   * @param concepts the concepts to load
   */
  private void createIndex(
      final String indexName, final String version, final List<Concept> concepts) {
    for (final Concept concept : concepts) {
      concept.setTerminology(TERMINOLOGY);
      concept.setVersion(version);
    }
    final IndexCoordinates index = IndexCoordinates.of(indexName);
    final IndexOperations indexOperations = operations.indexOps(index);
    indexOperations.delete();
    indexOperations.create();
    indexOperations.putMapping(Concept.class);
    operations.save(concepts, index);
    indexOperations.refresh();
  }

  /**
   * Builds a concept with a normalized name and the given roles.
   *
   * @param code the concept code
   * @param name the preferred name
   * @param roles the concept's roles
   * @return the concept
   */
  private static Concept getConcept(final String code, final String name, final Role... roles) {
    final Concept concept = new Concept();
    concept.setCode(code);
    concept.setName(name);
    concept.setNormName(ConceptUtils.normalize(name));
    concept.setRoles(List.of(roles));
    return concept;
  }

  /**
   * Builds a role.
   *
   * @param type the role's name
   * @param code the role's code
   * @param relatedCode the code of the concept the role points to
   * @param relatedName the name of the concept the role points to
   * @return the role
   */
  private static Role getRole(
      final String type, final String code, final String relatedCode, final String relatedName) {
    final Role role = new Role();
    role.setType(type);
    role.setCode(code);
    role.setRelatedCode(relatedCode);
    role.setRelatedName(relatedName);
    return role;
  }
}
