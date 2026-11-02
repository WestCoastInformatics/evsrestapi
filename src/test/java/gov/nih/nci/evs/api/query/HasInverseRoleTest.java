package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.Role;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.service.OpensearchOperationsService;
import gov.nih.nci.evs.api.util.ConceptUtils;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
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
import org.opensearch.index.query.QueryBuilders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * A tutorial for {@link HasInverseRole}, which finds concepts by the roles other concepts hold that
 * point to them. Read {@link HasRoleTest} first: type and target matching work the same way, so
 * this tutorial covers what changes when the role is seen from the other end.
 *
 * <p>An inverse role is a role seen from the concept it points to. When Cutaneous Melanoma's roles
 * include "primary site: Skin", Skin's inverse roles include "primary site: Cutaneous Melanoma".
 * The type stays the same, and the inverse role's target is the concept holding the role, here
 * called its source. So {@code getHasInverseRole(PRIMARY_SITE, CUTANEOUS_MELANOMA)} finds Skin.
 *
 * <p>Every lesson follows the same steps:
 *
 * <ol>
 *   <li>A small index holds the concepts in {@link #INDEXED_CONCEPTS}, mapped exactly like a real
 *       concept index, so the nested inverse roles mapping is tested too. Each concept's inverse
 *       roles are built from the other concepts' roles, the way the loader builds them.
 *   <li>The lesson builds a query, e.g. {@code getHasInverseRole(PRIMARY_SITE,
 *       CUTANEOUS_MELANOMA)}.
 *   <li>The query runs against the index, and the lesson checks which concept names came back.
 * </ol>
 *
 * <p>The role names and codes are made up for the tutorial. They look like NCIt's but don't need to
 * match it.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class HasInverseRoleTest {

  /** A throwaway index used only by this tutorial. */
  private static final String INDEX_NAME = "evs_has_inverse_role_tutorial";

  /**
   * A second throwaway index, standing in for the next version of the same terminology. It reuses
   * Skin's code for a concept named "Integument", so codes alone can't tell the versions apart.
   */
  private static final String NEXT_VERSION_INDEX_NAME =
      "evs_has_inverse_role_tutorial_next_version";

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

  /** Made-up role linking a concept to a disease it's a complication of. */
  private static final String COMPLICATION_OF = "Complication_Of_Disease";

  /** Code of {@link #COMPLICATION_OF}. */
  private static final String COMPLICATION_OF_CODE = "R999";

  /** Made-up role linking an anatomic structure to the structure it is part of. */
  private static final String PART_OF = "Anatomic_Structure_Is_Physical_Part_Of";

  /** Code of {@link #PART_OF}. */
  private static final String PART_OF_CODE = "R998";

  /** Role source: Cutaneous Melanoma, whose primary site is Skin. */
  private static final String CUTANEOUS_MELANOMA = "C01";

  /** Role source: Lung Carcinoma, whose primary site is Lung and associated site is Skin. */
  private static final String LUNG_CARCINOMA = "C02";

  /** Role source: Basal Cell Carcinoma of Skin, whose primary site is Skin. */
  private static final String BASAL_CELL_CARCINOMA = "C03";

  /** Melanoma, which holds no roles, so no inverse role points back to it. */
  private static final String MELANOMA = "C04";

  /** Role source: Skin Ulcer, a complication of Cutaneous Melanoma. */
  private static final String SKIN_ULCER = "C05";

  /** Role source: Lung Adenocarcinoma, whose primary site is Lung. */
  private static final String LUNG_ADENOCARCINOMA = "C07";

  /** A code prefix shared by every disease and complication, but no anatomic structure. */
  private static final String DISEASE_CODE_PREFIX = "C0";

  /** Site: Skin, part of Body Surface. */
  private static final String SKIN = "C12470";

  /** Site: Lung. */
  private static final String LUNG = "C12468";

  /** Site: Liver. No role points to it, so it has no inverse roles. */
  private static final String LIVER = "C12392";

  /** Made-up structure that Skin is part of. */
  private static final String BODY_SURFACE = "C99001";

  /**
   * The concepts in the index. Their roles are written out, and their inverse roles are built from
   * those roles. That gives Skin inverse roles from Cutaneous Melanoma and Basal Cell Carcinoma of
   * Skin (primary site) and Lung Carcinoma (associated site). Lung gets inverse roles from Lung
   * Carcinoma and Lung Adenocarcinoma (primary site), Cutaneous Melanoma from Skin Ulcer
   * (complication of), and Body Surface from Skin (part of).
   */
  private static final List<Concept> INDEXED_CONCEPTS =
      getConceptsWithInverseRoles(
          List.of(
              getConcept(
                  CUTANEOUS_MELANOMA,
                  "Cutaneous Melanoma",
                  getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN, "Skin")),
              getConcept(
                  LUNG_CARCINOMA,
                  "Lung Carcinoma",
                  getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, LUNG, "Lung"),
                  getRole(ASSOCIATED_SITE, ASSOCIATED_SITE_CODE, SKIN, "Skin")),
              getConcept(
                  BASAL_CELL_CARCINOMA,
                  "Basal Cell Carcinoma of Skin",
                  getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN, "Skin")),
              getConcept(MELANOMA, "Melanoma"),
              getConcept(
                  LUNG_ADENOCARCINOMA,
                  "Lung Adenocarcinoma",
                  getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, LUNG, "Lung")),
              getConcept(
                  SKIN_ULCER,
                  "Skin Ulcer",
                  getRole(
                      COMPLICATION_OF,
                      COMPLICATION_OF_CODE,
                      CUTANEOUS_MELANOMA,
                      "Cutaneous Melanoma")),
              getConcept(
                  SKIN, "Skin", getRole(PART_OF, PART_OF_CODE, BODY_SURFACE, "Body Surface")),
              getConcept(LUNG, "Lung"),
              getConcept(LIVER, "Liver"),
              getConcept(BODY_SURFACE, "Body Surface")));

  /**
   * The concepts in {@link #NEXT_VERSION_INDEX_NAME}. Skin's code now names "Integument", whose
   * primary site points back to "Melanoma of Integument", reusing Cutaneous Melanoma's code.
   * "Integument Ulcer" reuses Skin Ulcer's code and is a complication of it.
   */
  private static final List<Concept> NEXT_VERSION_CONCEPTS =
      getConceptsWithInverseRoles(
          List.of(
              getConcept(SKIN, "Integument"),
              getConcept(
                  CUTANEOUS_MELANOMA,
                  "Melanoma of Integument",
                  getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN, "Integument")),
              getConcept(
                  SKIN_ULCER,
                  "Integument Ulcer",
                  getRole(
                      COMPLICATION_OF,
                      COMPLICATION_OF_CODE,
                      CUTANEOUS_MELANOMA,
                      "Melanoma of Integument"))));

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
  // Lesson 1: HasInverseRole finds the concepts a source's role of the given type points to.
  // ---------------------------------------------------------------------------------------------

  /** Cutaneous Melanoma's primary site is Skin, so Skin is found. */
  @Test
  public void testSourceFindsConceptItsRolePointsTo() {
    final EvsQuery primarySitesOfCutaneousMelanoma =
        getHasInverseRole(PRIMARY_SITE, CUTANEOUS_MELANOMA);

    assertEquals(Set.of("Skin"), fetchMatchingNames(primarySitesOfCutaneousMelanoma));
  }

  /**
   * Lung Carcinoma points to Lung as its primary site and to Skin as its associated site. Skin has
   * a primary site inverse role and an inverse role from Lung Carcinoma, but on two different
   * entries, so only Lung matches.
   */
  @Test
  public void testRoleTypeAndSourceMatchOnSameInverseRoleEntry() {
    final EvsQuery primarySitesOfLungCarcinoma = getHasInverseRole(PRIMARY_SITE, LUNG_CARCINOMA);

    assertEquals(Set.of("Lung"), fetchMatchingNames(primarySitesOfLungCarcinoma));
  }

  /** The associated-site role from Lung Carcinoma reaches only Skin. */
  @Test
  public void testOtherRoleTypeFindsItsOwnConcepts() {
    final EvsQuery associatedSitesOfLungCarcinoma =
        getHasInverseRole(ASSOCIATED_SITE, LUNG_CARCINOMA);

    assertEquals(Set.of("Skin"), fetchMatchingNames(associatedSitesOfLungCarcinoma));
  }

  /** Melanoma holds no roles, so nothing is its primary site. */
  @Test
  public void testSourceWithoutRolesFindsNothing() {
    final EvsQuery primarySitesOfMelanoma = getHasInverseRole(PRIMARY_SITE, MELANOMA);

    assertEquals(Set.of(), fetchMatchingNames(primarySitesOfMelanoma));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: HasInverseRole searches inverse roles, never the concept's own roles.
  // ---------------------------------------------------------------------------------------------

  /**
   * Without a source, every concept that is some disease's primary site is found: Skin and Lung.
   * The diseases themselves hold primary site roles, but those are their roles, not inverse roles,
   * so they don't match. Liver is a site, but no disease points to it.
   */
  @Test
  public void testRoleTypeWithoutSourceFindsAnyConceptPointedTo() {
    final EvsQuery primarySitesOfAnyDisease =
        new HasInverseRole(terminologies, new RelationshipType(new Exact(PRIMARY_SITE)));

    assertEquals(Set.of("Skin", "Lung"), fetchMatchingNames(primarySitesOfAnyDisease));
  }

  /**
   * HasInverseRole finds the other end of a role from HasRole. Skin Ulcer holds the complication
   * role, so HasRole finds it. Cutaneous Melanoma is what the role points to, so HasInverseRole
   * finds that instead.
   */
  @Test
  public void testInverseRoleFindsOtherEndOfRole() {
    final EvsQuery diseasesWithComplications =
        new HasInverseRole(terminologies, new RelationshipType(new Exact(COMPLICATION_OF)));

    assertEquals(Set.of("Cutaneous Melanoma"), fetchMatchingNames(diseasesWithComplications));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 3: a source is a query, run before the inverse role query.
  // ---------------------------------------------------------------------------------------------

  /**
   * The source query runs first: diseases whose primary site is Lung (Lung Carcinoma, Lung
   * Adenocarcinoma). Then the outer query: anything that is any kind of site of one of them. Lung
   * is the primary site of both. Skin is Lung Carcinoma's associated site.
   */
  @Test
  public void testHasRoleAsSourceFindsSitesOfMatchingDiseases() {
    final EvsQuery diseasesWithPrimarySiteLung =
        new HasRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(new Exact(LUNG)));
    final EvsQuery sitesOfLungDiseases =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new CaseInsensitive(new Contains("site"))),
            new RelationshipTarget(diseasesWithPrimarySiteLung));

    assertEquals(Set.of("Lung", "Skin"), fetchMatchingNames(sitesOfLungDiseases));
  }

  /**
   * A source query can be a HasInverseRole too. Diseases that have a complication: Cutaneous
   * Melanoma. Their primary sites: Skin.
   */
  @Test
  public void testSourceCanHaveItsOwnInverseRoleSource() {
    final EvsQuery diseasesWithComplications =
        new HasInverseRole(terminologies, new RelationshipType(new Exact(COMPLICATION_OF)));
    final EvsQuery primarySitesOfDiseasesWithComplications =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(diseasesWithComplications));

    assertEquals(Set.of("Skin"), fetchMatchingNames(primarySitesOfDiseasesWithComplications));
  }

  /**
   * HasInverseRole can be the target query of a HasRole. Sites that are some disease's associated
   * site: Skin. Diseases whose primary site is one of them: Cutaneous Melanoma and Basal Cell
   * Carcinoma of Skin. Lung Carcinoma points to Skin only as its associated site, so it doesn't
   * match.
   */
  @Test
  public void testHasInverseRoleAsTargetOfHasRole() {
    final EvsQuery associatedSites =
        new HasInverseRole(terminologies, new RelationshipType(new Exact(ASSOCIATED_SITE)));
    final EvsQuery diseasesWithPrimarySiteThatIsAnAssociatedSite =
        new HasRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(associatedSites));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(diseasesWithPrimarySiteThatIsAnAssociatedSite));
  }

  /**
   * A source query matching no concepts finds nothing, rather than matching any source. Nothing is
   * a complication of Lung Carcinoma, so nothing is the primary site of such a complication.
   */
  @Test
  public void testSourceMatchingNothingFindsNothing() {
    final EvsQuery complicationsOfLungCarcinoma =
        new HasRole(
            terminologies,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(new Exact(LUNG_CARCINOMA)));
    final EvsQuery primarySitesOfComplicationsOfLungCarcinoma =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(complicationsOfLungCarcinoma));

    assertEquals(Set.of(), fetchMatchingNames(primarySitesOfComplicationsOfLungCarcinoma));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: a source only matches inverse roles in the terminology version it was found in.
  // ---------------------------------------------------------------------------------------------

  /**
   * Searching both versions, the source "diseases with a complication whose name has the word skin"
   * is only found in the first version: Cutaneous Melanoma, a complication of which is Skin Ulcer.
   * In the next version, Integument is the primary site of the same code, but there that code's
   * complication is Integument Ulcer, so Integument doesn't match.
   */
  @Test
  public void testSourceFoundInOneVersionOnlyMatchesInverseRolesInThatVersion() {
    final EvsQuery diseasesWithComplicationNamedSkin =
        new HasInverseRole(
            bothVersions,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(new CaseInsensitive(new Exact("skin"))));
    final EvsQuery primarySitesOfThoseDiseases =
        new HasInverseRole(
            bothVersions,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(diseasesWithComplicationNamedSkin));

    assertEquals(Set.of("Skin"), fetchMatchingNames(primarySitesOfThoseDiseases));
  }

  /**
   * The source "diseases with a complication whose code is Skin Ulcer's" is found in both versions,
   * Cutaneous Melanoma's code in each, so its primary site matches in both.
   */
  @Test
  public void testSourceFoundInBothVersionsMatchesInverseRolesInBoth() {
    final EvsQuery diseasesWithComplicationOfSkinUlcerCode =
        new HasInverseRole(
            bothVersions,
            new RelationshipType(new Exact(COMPLICATION_OF)),
            new RelationshipTarget(new Exact(SKIN_ULCER)));
    final EvsQuery primarySitesOfThoseDiseases =
        new HasInverseRole(
            bothVersions,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(diseasesWithComplicationOfSkinUlcerCode));

    assertEquals(Set.of("Skin", "Integument"), fetchMatchingNames(primarySitesOfThoseDiseases));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 5: the role type and a source match work as in HasRole.
  // ---------------------------------------------------------------------------------------------

  /** The role's code finds the same concepts as its name. */
  @Test
  public void testRoleCodeMatchesLikeName() {
    final EvsQuery byName = getHasInverseRole(PRIMARY_SITE, CUTANEOUS_MELANOMA);
    final EvsQuery byCode = getHasInverseRole(PRIMARY_SITE_CODE, CUTANEOUS_MELANOMA);

    assertEquals(fetchMatchingNames(byName), fetchMatchingNames(byCode));
  }

  /** Part of a role name is not a role name. */
  @Test
  public void testPartialRoleNameFindsNothing() {
    final EvsQuery conceptsPointedToByRoleNamedAnatomicSite =
        new HasInverseRole(terminologies, new RelationshipType(new Exact("Anatomic_Site")));

    assertEquals(Set.of(), fetchMatchingNames(conceptsPointedToByRoleNamedAnatomicSite));
  }

  /** Role types are case sensitive by default. */
  @Test
  public void testRoleTypeIsCaseSensitive() {
    final EvsQuery lowercasePrimarySitesOfCutaneousMelanoma =
        getHasInverseRole(PRIMARY_SITE.toLowerCase(), CUTANEOUS_MELANOMA);

    assertEquals(Set.of(), fetchMatchingNames(lowercasePrimarySitesOfCutaneousMelanoma));
  }

  /** With its match wrapped in CaseInsensitive, the role type matches whatever its case. */
  @Test
  public void testCaseInsensitiveRoleTypeMatchesAnyCase() {
    final RelationshipType roleType =
        new RelationshipType(new CaseInsensitive(new Exact(PRIMARY_SITE.toLowerCase())));
    final EvsQuery anyCasePrimarySitesOfCutaneousMelanoma =
        getHasInverseRole(roleType, CUTANEOUS_MELANOMA);

    assertEquals(Set.of("Skin"), fetchMatchingNames(anyCasePrimarySitesOfCutaneousMelanoma));
  }

  /** CaseInsensitive also works on a role code. */
  @Test
  public void testCaseInsensitiveRoleCode() {
    final RelationshipType roleType =
        new RelationshipType(new CaseInsensitive(new Exact(PRIMARY_SITE_CODE.toLowerCase())));
    final EvsQuery anyCasePrimarySiteCodeOfCutaneousMelanoma =
        getHasInverseRole(roleType, CUTANEOUS_MELANOMA);

    assertEquals(Set.of("Skin"), fetchMatchingNames(anyCasePrimarySiteCodeOfCutaneousMelanoma));
  }

  /**
   * A role type takes any value match, not just an exact name. "Anatomic_Site" is in both the
   * primary and the associated site role names, so both of Lung Carcinoma's sites are found: Lung
   * (primary) and Skin (associated).
   */
  @Test
  public void testRoleTypeMatchedByContains() {
    final RelationshipType roleType = new RelationshipType(new Contains("Anatomic_Site"));
    final EvsQuery anyAnatomicSiteOfLungCarcinoma = getHasInverseRole(roleType, LUNG_CARCINOMA);

    assertEquals(Set.of("Lung", "Skin"), fetchMatchingNames(anyAnatomicSiteOfLungCarcinoma));
  }

  /** A source given as a match is compared against the source's code. */
  @Test
  public void testSourceMatchedByCode() {
    final EvsQuery primarySitesOfLungAdenocarcinoma =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(new Exact(LUNG_ADENOCARCINOMA)));

    assertEquals(Set.of("Lung"), fetchMatchingNames(primarySitesOfLungAdenocarcinoma));
  }

  /**
   * A source match can match several sources. Every role whose type contains "Anatomic", site or
   * part of, is found if its source's code starts with {@link #DISEASE_CODE_PREFIX}. Skin and Lung
   * are sites of diseases. Body Surface is pointed to only by Skin, whose code doesn't start with
   * the prefix, so it doesn't match.
   */
  @Test
  public void testSourceMatchedByCodePrefixFindsAnyOfThem() {
    final EvsQuery anatomicConceptsPointedToByDiseases =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new Contains("Anatomic")),
            new RelationshipTarget(new StartsWith(DISEASE_CODE_PREFIX)));

    assertEquals(Set.of("Skin", "Lung"), fetchMatchingNames(anatomicConceptsPointedToByDiseases));
  }

  /** A source given as a match that matches neither a source name nor a code finds nothing. */
  @Test
  public void testUnmatchedSourceNameOrCodeFindsNothing() {
    final EvsQuery primarySitesOfDiseaseNamedExactlyLiver =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(new Exact("Liver")));

    assertEquals(Set.of(), fetchMatchingNames(primarySitesOfDiseaseNamedExactlyLiver));
  }

  /**
   * A source given as a match is compared against the source's name, with no search for the source
   * first. "melanomma" is one edit from the word "melanoma" in "Cutaneous Melanoma".
   */
  @Test
  public void testSourceMatchedByFuzzyName() {
    final EvsQuery primarySitesOfDiseaseNamedLikeMelanomma =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new Exact(PRIMARY_SITE)),
            new RelationshipTarget(new Fuzzy("melanomma")));

    assertEquals(Set.of("Skin"), fetchMatchingNames(primarySitesOfDiseaseNamedLikeMelanomma));
  }

  /**
   * The type and the source can each be matched in any fashion: any role with "site" in its name,
   * whatever the case, from a source with "lung" in its name. Lung is the primary site of both lung
   * diseases. Skin is Lung Carcinoma's associated site.
   */
  @Test
  public void testTypeAndSourceMatchedByCaseInsensitiveContains() {
    final EvsQuery sitesOfDiseasesNamedLung =
        new HasInverseRole(
            terminologies,
            new RelationshipType(new CaseInsensitive(new Contains("site"))),
            new RelationshipTarget(new CaseInsensitive(new Contains("lung"))));

    assertEquals(Set.of("Lung", "Skin"), fetchMatchingNames(sitesOfDiseasesNamedLung));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 6: HasInverseRole combines with other queries.
  // ---------------------------------------------------------------------------------------------

  /**
   * Sites that are some disease's primary site, and are part of Body Surface: Skin. One condition
   * is on the concept's inverse roles, the other on its roles. Combined with a plain bool query
   * until the And node exists.
   */
  @Test
  public void testCombinesWithHasRole() {
    final EvsQuery isAPrimarySite =
        new HasInverseRole(terminologies, new RelationshipType(new Exact(PRIMARY_SITE)));
    final EvsQuery isPartOfBodySurface =
        new HasRole(
            terminologies,
            new RelationshipType(new Exact(PART_OF)),
            new RelationshipTarget(new Exact(BODY_SURFACE)));
    final QueryBuilder primarySitesOnBodySurface =
        QueryBuilders.boolQuery()
            .must(isAPrimarySite.fetchOpenSearchQuery(operations))
            .must(isPartOfBodySurface.fetchOpenSearchQuery(operations));

    assertEquals(Set.of("Skin"), fetchMatchingNames(primarySitesOnBodySurface));
  }

  /** A role type is required. */
  @Test
  public void testMissingRoleTypeThrowsErrors() {
    assertThrows(NullPointerException.class, () -> new HasInverseRole(terminologies, null));
  }

  /**
   * Builds a {@link HasInverseRole} whose source is one concept, given by its exact code.
   *
   * @param roleNameOrCode the role's exact name or code
   * @param sourceCode the code of the concept holding the role
   * @return the query
   */
  private HasInverseRole getHasInverseRole(final String roleNameOrCode, final String sourceCode) {
    return getHasInverseRole(new RelationshipType(new Exact(roleNameOrCode)), sourceCode);
  }

  /**
   * Builds a {@link HasInverseRole} whose source is one concept, given by its exact code.
   *
   * @param roleType the role's type
   * @param sourceCode the code of the concept holding the role
   * @return the query
   */
  private HasInverseRole getHasInverseRole(
      final RelationshipType roleType, final String sourceCode) {
    return new HasInverseRole(
        terminologies, roleType, new RelationshipTarget(new Exact(sourceCode)));
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
   * Runs the OpenSearch query against the tutorial index.
   *
   * @param query the query to run
   * @return the names of the matching concepts
   */
  private Set<String> fetchMatchingNames(final QueryBuilder query) {
    return fetchMatchingNames(query, IndexCoordinates.of(INDEX_NAME));
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
   * Gives each concept the inverse roles of the roles pointing to it, the way the loader does: the
   * same type and code, pointing back to the concept holding the role.
   *
   * @param concepts the concepts, with their roles set
   * @return the same concepts, with their inverse roles set
   */
  private static List<Concept> getConceptsWithInverseRoles(final List<Concept> concepts) {
    final Map<String, Concept> conceptsByCode =
        concepts.stream().collect(Collectors.toMap(Concept::getCode, Function.identity()));
    for (final Concept source : concepts) {
      addInverseRolesOf(source, conceptsByCode);
    }
    return concepts;
  }

  /**
   * Adds an inverse role to each indexed concept the source's roles point to. Roles pointing to a
   * concept that isn't indexed have nowhere to go.
   *
   * @param source the concept holding the roles
   * @param conceptsByCode the indexed concepts
   */
  private static void addInverseRolesOf(
      final Concept source, final Map<String, Concept> conceptsByCode) {
    for (final Role role : source.getRoles()) {
      final Concept target = conceptsByCode.get(role.getRelatedCode());
      final boolean isTargetIndexed = target != null;
      if (isTargetIndexed) {
        target
            .getInverseRoles()
            .add(getRole(role.getType(), role.getCode(), source.getCode(), source.getName()));
      }
    }
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
   * Builds a role or inverse role.
   *
   * @param type the role's name
   * @param code the role's code
   * @param relatedCode the code of the concept at the other end
   * @param relatedName the name of the concept at the other end
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
