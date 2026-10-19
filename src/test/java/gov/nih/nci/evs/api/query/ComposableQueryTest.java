package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import gov.nih.nci.evs.api.model.Concept;
import gov.nih.nci.evs.api.model.IncludeParam;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.service.OpensearchQueryService;
import gov.nih.nci.evs.api.util.TerminologyUtils;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Target for the composable query tree: one query that uses every capability in the Phase 1 Explore
 * table (rows 1.2 to 1.14).
 *
 * <p>This test is expected to FAIL TO COMPILE until the planned classes exist. The compiler errors
 * list what is left to build. Only the atomic queries exist today: {@link Match}, {@link Contains},
 * {@link StartsWith}, and {@link Fuzzy}, plus the {@link CaseInsensitive} wrapper. Everything else
 * (And, Or, Not, DescendantOf, DescendantOrSelfOf, HasRole, HasAssociation, HasProperty,
 * HasQualifier, MemberOfSet, Reachable, ConceptSetRequest, ConceptSetService, and their helpers) is
 * planned. See query/README.md.
 *
 * <p>Boolean logic is used rather than set operations (union, intersection, difference). They are
 * equivalent here, and one vocabulary keeps one way to write each query.
 *
 * <p>{@link Typeahead} and {@link SearchAsYouType} are left out on purpose. They build on query
 * nodes but are a completion feature, not part of the query language modeled here.
 */
@ExtendWith(SpringExtension.class)
@SpringBootTest
public class ComposableQueryTest {

  /** Melanoma, the hierarchy branch and traversal seed. */
  private static final String MELANOMA_CODE = "C3224";

  /** Skin, the role target branch. */
  private static final String SKIN_CODE = "C12470";

  /** BRAF Gene, the association target. */
  private static final String BRAF_GENE_CODE = "C18363";

  /** Role linking a disease to its primary anatomic site. */
  private static final String PRIMARY_ANATOMIC_SITE_ROLE = "Disease_Has_Primary_Anatomic_Site";

  /** Association linking a concept to a genetic biomarker. */
  private static final String GENETIC_BIOMARKER_ASSOCIATION = "Related_To_Genetic_Biomarker";

  /** Property holding the semantic type. */
  private static final String SEMANTIC_TYPE_PROPERTY = "Semantic_Type";

  /** Semantic type value for neoplasms. */
  private static final String NEOPLASTIC_PROCESS = "Neoplastic Process";

  /** Property holding a contributing source. */
  private static final String CONTRIBUTING_SOURCE_PROPERTY = "Contributing_Source";

  /** Qualifier naming the source of a property value. */
  private static final String SOURCE_QUALIFIER = "source";

  /** The CTRP contributing source. */
  private static final String CTRP_SOURCE = "CTRP";

  /** Concept status of retired concepts. */
  private static final String RETIRED_STATUS = "Retired_Concept";

  /** TODO: confirm code. A subset the results may belong to. */
  private static final String CTRP_DISEASE_SUBSET_CODE = "C116977";

  /** TODO: confirm code. Another subset the results may belong to. */
  private static final String CDISC_SDTM_SUBSET_CODE = "C61410";

  /** TODO: confirm code. A subset the results must not belong to. */
  private static final String EXCLUDED_SUBSET_CODE = "C176707";

  /** Normalized prefix for the starts-with branch. */
  private static final String NAME_PREFIX = "cutaneous";

  /** Normalized text for the contains branch. */
  private static final String CONTAINED_TERM = "cutaneous melanoma";

  /** Misspelled text for the fuzzy branch. */
  private static final String MISSPELLED_TERM = "cutaneus melanoma";

  /** How many hops the traversal may take. */
  private static final int MAX_DEPTH = 3;

  /** How many nodes the traversal may visit. */
  private static final int MAX_NODES = 10_000;

  /** How long the traversal may run. */
  private static final Duration TRAVERSAL_TIMEOUT = Duration.ofSeconds(5);

  /** First page of results. */
  private static final int FIRST_PAGE = 0;

  /** Results per page. */
  private static final int PAGE_SIZE = 25;

  /** Reads indexed terminologies. */
  @Autowired private OpensearchQueryService opensearchQueryService;

  /** Resolves terminology names to indexed terminologies. */
  @Autowired private TerminologyUtils termUtils;

  /** Runs concept-set requests. Planned. */
  @Autowired private ConceptSetService conceptSetService;

  /**
   * Runs the query that combines every Phase 1 capability and checks the response contract.
   *
   * @throws Exception the exception
   */
  @Test
  public void testEveryPhaseOneCapabilityInOneQuery() throws Exception {
    final Terminology ncitTerminology =
        termUtils.getIndexedTerminology("ncit", opensearchQueryService, true);
    final List<Terminology> ncit = List.of(ncitTerminology);

    // 1.9 Boolean logic: every included predicate, and not any excluded one
    final EvsQuery query =
        new And(
            ncit,
            List.of(
                // 1.2 Lexical match modes
                new Or(
                    ncit,
                    List.of(
                        new StartsWith(ncit, "normName", NAME_PREFIX),
                        new CaseInsensitive(new Contains(ncit, "normName", CONTAINED_TERM)),
                        new Fuzzy(ncit, "normName", MISSPELLED_TERM))),

                // 1.3 Hierarchy branch, strictly below Melanoma
                new DescendantOf(ncit, MELANOMA_CODE),

                // 1.4 Role type + role target (the target is itself a query): Skin or below
                new HasRole(
                    ncit, PRIMARY_ANATOMIC_SITE_ROLE, new DescendantOrSelfOf(ncit, SKIN_CODE)),

                // 1.5 Association type + direction + target
                new HasAssociation(
                    ncit,
                    GENETIC_BIOMARKER_ASSOCIATION,
                    Direction.OUTBOUND,
                    new Match(ncit, "code", BRAF_GENE_CODE)),

                // 1.6 Several property predicates, one with a qualifier
                new HasProperty(
                    ncit,
                    SEMANTIC_TYPE_PROPERTY,
                    new Match(ncit, "properties.value", NEOPLASTIC_PROCESS)),
                new HasProperty(
                    ncit,
                    CONTRIBUTING_SOURCE_PROPERTY,
                    new Match(ncit, "properties.value", CTRP_SOURCE),
                    new HasQualifier(
                        ncit,
                        SOURCE_QUALIFIER,
                        new Match(ncit, "properties.qualifiers.value", CTRP_SOURCE))),

                // 1.6 Status as a keyword field
                new Match(ncit, "active", Boolean.TRUE.toString()),

                // 1.7 Subsets: in A or B (and not in C, below)
                new Or(
                    ncit,
                    List.of(
                        new MemberOfSet(ncit, CTRP_DISEASE_SUBSET_CODE),
                        new MemberOfSet(ncit, CDISC_SDTM_SUBSET_CODE))),

                // 1.11 Bounded traversal as a filter: reachable from the seeds within the budget
                new Reachable(
                    ncit,
                    Set.of(MELANOMA_CODE),
                    List.of(
                        Edge.hierarchy(Direction.DOWN),
                        Edge.role(PRIMARY_ANATOMIC_SITE_ROLE, Direction.OUTBOUND)),
                    new TraversalBudget(MAX_DEPTH, MAX_NODES, TRAVERSAL_TIMEOUT)),

                // 1.6 not retired, and 1.7 not in the excluded subset
                new Not(
                    ncit,
                    new Or(
                        ncit,
                        List.of(
                            new Match(ncit, "conceptStatus", RETIRED_STATUS),
                            new MemberOfSet(ncit, EXCLUDED_SUBSET_CODE))))));

    // 1.8 facets, 1.10 concept set, 1.12 result handling, 1.13 provenance, 1.14 subtree
    final ConceptSetRequest request =
        new ConceptSetRequest(query)
            .withPage(PageRequest.of(FIRST_PAGE, PAGE_SIZE))
            .withHighlight("name", "synonyms.name")
            .withFacets(
                new PropertyFacet(SEMANTIC_TYPE_PROPERTY),
                new SubsetFacet(),
                new FieldFacet("conceptStatus"))
            .withInclude(new IncludeParam("minimal,properties"))
            .asSubtreeOf(MELANOMA_CODE);

    final ConceptSetResponse response = conceptSetService.fetch(request);

    // 1.10 / 1.12: a bounded, paged concept set
    assertTrue(response.getConcepts().size() <= PAGE_SIZE);
    for (final Concept concept : response.getConcepts()) {
      assertTrue(concept.getActive());
    }

    // 1.8: one facet per requested facet
    final int requestedFacetCount = 3;
    assertEquals(requestedFacetCount, response.getFacets().size());

    // 1.13: provenance names the terminology and release searched
    assertEquals(ncitTerminology.getTerminology(), response.getProvenance().getTerminology());
    assertEquals(ncitTerminology.getVersion(), response.getProvenance().getVersion());

    // 1.11 / 1.13: truncation is reported explicitly
    assertFalse(response.getTruncation().getIsTruncated());
  }
}
