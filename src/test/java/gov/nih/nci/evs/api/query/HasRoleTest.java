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
 * <p>A role target is a query over other concepts. Building a {@link HasRole} runs its target query
 * first, so the target concepts (Skin, Lung, Liver) are in the index too.
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

  /** A code no indexed concept has. */
  private static final String UNUSED_CODE = "C00000";

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

  /** Field holding a concept's code, which target queries match on. */
  private static final String CODE_FIELD = "code";

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

  /**
   * The concepts in the index. "Lung Carcinoma" has both sites, but Skin only through the
   * associated-site role, which lesson 1 relies on. Skin, Lung, and Liver are role targets. "Skin
   * Ulcer" is a complication of a disease, which lesson 2b's nested target relies on.
   */
  private static final List<Concept> INDEXED_CONCEPTS =
      List.of(
          getConcept("C01", "Cutaneous Melanoma", getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN)),
          getConcept(
              "C02",
              "Lung Carcinoma",
              getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, LUNG),
              getRole(ASSOCIATED_SITE, ASSOCIATED_SITE_CODE, SKIN)),
          getConcept(
              "C03",
              "Basal Cell Carcinoma of Skin",
              getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN)),
          getConcept("C04", "Melanoma"),
          getConcept("C05", "Skin Ulcer", getRole(COMPLICATION_OF, COMPLICATION_OF_CODE, "C01")),
          getConcept(SKIN, "Skin"),
          getConcept(LUNG, "Lung"),
          getConcept(LIVER, "Liver"));

  /**
   * The concepts in {@link #NEXT_VERSION_INDEX_NAME}. Skin's code now names "Integument", and a
   * melanoma points to it as its primary site.
   */
  private static final List<Concept> NEXT_VERSION_CONCEPTS =
      List.of(
          getConcept(SKIN, "Integument"),
          getConcept(
              "C06", "Melanoma of Integument", getRole(PRIMARY_SITE, PRIMARY_SITE_CODE, SKIN)));

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

    terminologies = List.of(getTerminology(INDEX_NAME));
    bothVersions = List.of(getTerminology(INDEX_NAME), getTerminology(NEXT_VERSION_INDEX_NAME));
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
    final EvsQuery query = getHasRole(PRIMARY_SITE, SKIN);

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"), fetchMatchingNames(query));
  }

  /** Nothing has a primary site of Liver. */
  @Test
  public void testUnmatchedTargetFindsNothing() {
    final EvsQuery query = getHasRole(PRIMARY_SITE, LIVER);

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  /** The associated-site role reaches Skin only from "Lung Carcinoma". */
  @Test
  public void testOtherRoleTypeFindsItsOwnTargets() {
    final EvsQuery query = getHasRole(ASSOCIATED_SITE, SKIN);

    assertEquals(Set.of("Lung Carcinoma"), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2: the target is optional.
  // ---------------------------------------------------------------------------------------------

  /** Without a target, every concept with a primary site is found, whatever it points to. */
  @Test
  public void testRoleTypeWithoutTargetFindsAnyTarget() {
    final EvsQuery query = new HasRole(terminologies, new RoleType(PRIMARY_SITE));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Lung Carcinoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2b: a target is a query, run before the role query.
  // ---------------------------------------------------------------------------------------------

  /**
   * A target query can match several concepts, e.g. Skin and everything below it. Here the target
   * is every code starting with {@link #SKIN_AND_LUNG_CODE_PREFIX}, which finds Skin and Lung, so
   * every concept whose primary site is either one is found.
   */
  @Test
  public void testTargetMatchingSeveralConceptsFindsAnyOfThem() {
    final EvsQuery skinOrLung =
        new StartsWith(terminologies, CODE_FIELD, SKIN_AND_LUNG_CODE_PREFIX);
    final EvsQuery query =
        new HasRole(terminologies, new RoleType(PRIMARY_SITE), new RoleTarget(skinOrLung));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Lung Carcinoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(query));
  }

  /**
   * A target can have a role target of its own, to any depth. The inner target runs first: diseases
   * whose primary site is Skin (Cutaneous Melanoma, Basal Cell Carcinoma of Skin). Then the outer:
   * concepts that are a complication of one of them (Skin Ulcer).
   */
  @Test
  public void testTargetCanHaveItsOwnRoleTarget() {
    final EvsQuery skinDiseases = getHasRole(PRIMARY_SITE, SKIN);
    final EvsQuery query =
        new HasRole(terminologies, new RoleType(COMPLICATION_OF), new RoleTarget(skinDiseases));

    assertEquals(Set.of("Skin Ulcer"), fetchMatchingNames(query));
  }

  /** A target matching no concepts finds nothing, rather than matching any target. */
  @Test
  public void testTargetMatchingNothingFindsNothing() {
    final EvsQuery query = getHasRole(PRIMARY_SITE, UNUSED_CODE);

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 2c: a target only matches roles in the terminology version it was found in.
  // ---------------------------------------------------------------------------------------------

  /**
   * Searching both versions, the target "concepts named Skin" is only found in the first version.
   * The next version's melanoma points to the same code, but there that code names "Integument", so
   * it doesn't match.
   */
  @Test
  public void testTargetFoundInOneVersionOnlyMatchesRolesInThatVersion() {
    final EvsQuery namedSkin = new Match(bothVersions, "normName", ConceptUtils.normalize("Skin"));
    final EvsQuery query =
        new HasRole(bothVersions, new RoleType(PRIMARY_SITE), new RoleTarget(namedSkin));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"), fetchMatchingNames(query));
  }

  /** A target found in both versions matches roles in both. */
  @Test
  public void testTargetFoundInBothVersionsMatchesRolesInBoth() {
    final EvsQuery skinCode = new Match(bothVersions, CODE_FIELD, SKIN);
    final EvsQuery query =
        new HasRole(bothVersions, new RoleType(PRIMARY_SITE), new RoleTarget(skinCode));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin", "Melanoma of Integument"),
        fetchMatchingNames(query));
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
    final EvsQuery query = new HasRole(terminologies, new RoleType("Anatomic_Site"));

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  /** Role types are case sensitive by default. */
  @Test
  public void testRoleTypeIsCaseSensitive() {
    final EvsQuery query = getHasRole(PRIMARY_SITE.toLowerCase(), SKIN);

    assertEquals(Set.of(), fetchMatchingNames(query));
  }

  /**
   * Wrapped in CaseInsensitive, the role type matches whatever its case. The wrapped query is not
   * changed.
   */
  @Test
  public void testCaseInsensitiveMatchesAnyCase() {
    final HasRole caseSensitive = getHasRole(PRIMARY_SITE.toLowerCase(), SKIN);
    final EvsQuery caseInsensitive = new CaseInsensitive(caseSensitive);

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"),
        fetchMatchingNames(caseInsensitive));
    assertEquals(Set.of(), fetchMatchingNames(caseSensitive));
  }

  /** CaseInsensitive also works on a role code. */
  @Test
  public void testCaseInsensitiveRoleCode() {
    final EvsQuery query = new CaseInsensitive(getHasRole(PRIMARY_SITE_CODE.toLowerCase(), SKIN));

    assertEquals(
        Set.of("Cutaneous Melanoma", "Basal Cell Carcinoma of Skin"), fetchMatchingNames(query));
  }

  // ---------------------------------------------------------------------------------------------
  // Lesson 4: HasRole combines with other queries.
  // ---------------------------------------------------------------------------------------------

  /**
   * Primary site Skin, and a name starting with "cutaneous". Combined with a plain bool query until
   * the And node exists.
   */
  @Test
  public void testCombinesWithAtomicQuery() {
    final EvsQuery hasSkinSite = getHasRole(PRIMARY_SITE, SKIN);
    final EvsQuery cutaneous =
        new StartsWith(terminologies, "normName", ConceptUtils.normalize("Cutaneous"));
    final QueryBuilder query =
        QueryBuilders.boolQuery()
            .must(hasSkinSite.fetchOpenSearchQuery(operations))
            .must(cutaneous.fetchOpenSearchQuery(operations));

    assertEquals(Set.of("Cutaneous Melanoma"), fetchMatchingNames(query));
  }

  /** A role type and a target are required. */
  @Test
  public void testRejectsMissingValues() {
    assertThrows(NullPointerException.class, () -> new RoleType(null));
    assertThrows(NullPointerException.class, () -> new RoleTarget(null));
    assertThrows(NullPointerException.class, () -> new HasRole(terminologies, null));
  }

  /**
   * Builds a {@link HasRole} whose target is one concept, given by code.
   *
   * @param roleNameOrCode the role's exact name or code
   * @param targetCode the code of the concept the role must point to
   * @return the query
   */
  private HasRole getHasRole(final String roleNameOrCode, final String targetCode) {
    final EvsQuery targetQuery = new Match(terminologies, CODE_FIELD, targetCode);
    return new HasRole(terminologies, new RoleType(roleNameOrCode), new RoleTarget(targetQuery));
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
   * Builds a terminology whose concept index is the given index.
   *
   * @param indexName the index name
   * @return the terminology
   */
  private static Terminology getTerminology(final String indexName) {
    final Terminology terminology = new Terminology();
    terminology.setIndexName(indexName);
    return terminology;
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
   * @return the role
   */
  private static Role getRole(final String type, final String code, final String relatedCode) {
    final Role role = new Role();
    role.setType(type);
    role.setCode(code);
    role.setRelatedCode(relatedCode);
    return role;
  }
}
