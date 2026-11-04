package gov.nih.nci.evs.api.query.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.model.TerminologyMetadata;
import gov.nih.nci.evs.api.query.AllQualifiers;
import gov.nih.nci.evs.api.query.AncestorOf;
import gov.nih.nci.evs.api.query.AncestorOrSelfOf;
import gov.nih.nci.evs.api.query.And;
import gov.nih.nci.evs.api.query.AnyQualifier;
import gov.nih.nci.evs.api.query.Boost;
import gov.nih.nci.evs.api.query.CaseInsensitive;
import gov.nih.nci.evs.api.query.Contains;
import gov.nih.nci.evs.api.query.DescendantOf;
import gov.nih.nci.evs.api.query.DescendantOrSelfOf;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.Exact;
import gov.nih.nci.evs.api.query.Fuzzy;
import gov.nih.nci.evs.api.query.HasAssociation;
import gov.nih.nci.evs.api.query.HasInverseAssociation;
import gov.nih.nci.evs.api.query.HasInverseRole;
import gov.nih.nci.evs.api.query.HasProperty;
import gov.nih.nci.evs.api.query.HasRole;
import gov.nih.nci.evs.api.query.HasSynonym;
import gov.nih.nci.evs.api.query.InSubset;
import gov.nih.nci.evs.api.query.MatchAnyOf;
import gov.nih.nci.evs.api.query.NoQualifier;
import gov.nih.nci.evs.api.query.Not;
import gov.nih.nci.evs.api.query.OneOf;
import gov.nih.nci.evs.api.query.Or;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import gov.nih.nci.evs.api.query.QueryTestTerminologies;
import gov.nih.nci.evs.api.query.RelationshipTarget;
import gov.nih.nci.evs.api.query.RelationshipType;
import gov.nih.nci.evs.api.query.SearchAsYouType;
import gov.nih.nci.evs.api.query.StartsWith;
import gov.nih.nci.evs.api.query.Typeahead;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opensearch.common.unit.Fuzziness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Unit tests for {@link QueryJson}: every node, value match, and qualifier condition type writes
 * the expected JSON and reads back to a tree that writes the same JSON, as the model {@code
 * SerializationTester} checks for model objects. Comparing written JSON, rather than nodes, keeps
 * {@code equals} out of the nodes. No OpenSearch or Spring: nodes are only built, never run.
 *
 * <p>The same tests run on both serialization prototypes (DTOs and Jackson mix-ins), so they only
 * use {@link QueryJson}'s public methods.
 */
public class QueryJsonTest {

  /** The logger. */
  private static final Logger logger = LoggerFactory.getLogger(QueryJsonTest.class);

  /** Reads JSON into trees, whose objects compare equal whatever their property order. */
  private static final ObjectMapper TREE_READER = new ObjectMapper();

  /** The subset membership association of the made-up terminology, for {@link InSubset}. */
  private static final String SUBSET_MEMBERSHIP = "A8";

  /** The terminologies being searched: one terminology, as a request names in its path. */
  private static final List<Terminology> TERMINOLOGIES = List.of(getTerminology());

  /**
   * Returns each case: a name, a query tree, and the JSON it writes.
   *
   * @return the cases
   */
  private static Stream<Arguments> getQueriesAndJson() {
    final List<Terminology> t = TERMINOLOGIES;
    return Stream.of(
        Arguments.of(
            "descendantOf with a code",
            new DescendantOf(t, "C3262"),
            "{'type':'descendantOf','code':'C3262'}"),
        Arguments.of(
            "descendantOf with a max level",
            new DescendantOf(t, "C3262", 3),
            "{'type':'descendantOf','code':'C3262','maxLevel':3}"),
        Arguments.of(
            "descendantOrSelfOf",
            new DescendantOrSelfOf(t, "C4872"),
            "{'type':'descendantOrSelfOf','code':'C4872'}"),
        Arguments.of(
            "ancestorOf with a max level",
            new AncestorOf(t, "C4872", 1),
            "{'type':'ancestorOf','code':'C4872','maxLevel':1}"),
        Arguments.of(
            "ancestorOrSelfOf",
            new AncestorOrSelfOf(t, "C4872"),
            "{'type':'ancestorOrSelfOf','code':'C4872'}"),
        Arguments.of(
            "hierarchy node anchored on a hierarchy node",
            new DescendantOf(t, new DescendantOf(t, "C3262", 1)),
            "{'type':'descendantOf','anchor':{'type':'descendantOf','code':'C3262','maxLevel':1}}"),
        Arguments.of(
            "and holding not",
            new And(
                t, List.of(new DescendantOf(t, "C3262"), new Not(t, new DescendantOf(t, "C4872")))),
            "{'type':'and','children':[{'type':'descendantOf','code':'C3262'},"
                + "{'type':'not','child':{'type':'descendantOf','code':'C4872'}}]}"),
        Arguments.of(
            "or holding boost, typeahead, and matchAnyOf",
            new Or(
                t,
                List.of(
                    new Boost(t, new Typeahead(t, "mela"), 2f),
                    new MatchAnyOf(t, "code", List.of("C3262", "C3224")))),
            "{'type':'or','children':[{'type':'boost','child':{'type':'typeahead','term':'mela'},"
                + "'score':2.0},{'type':'matchAnyOf','field':'code','values':['C3224','C3262']}]}"),
        Arguments.of(
            "hasRole with a query target",
            new HasRole(
                t,
                new RelationshipType(new Exact("R101")),
                new RelationshipTarget(new DescendantOrSelfOf(t, "C12470"))),
            "{'type':'hasRole','relationshipType':{'type':'exact','term':'R101'},"
                + "'target':{'query':{'type':'descendantOrSelfOf','code':'C12470'}}}"),
        Arguments.of(
            "hasInverseRole without a target, ignoring case",
            new HasInverseRole(
                t, new RelationshipType(new CaseInsensitive(new StartsWith("disease_has")))),
            "{'type':'hasInverseRole','relationshipType':{'type':'caseInsensitive',"
                + "'match':{'type':'startsWith','term':'disease_has'}}}"),
        Arguments.of(
            "hasRole with one of several types",
            new HasRole(t, new RelationshipType(new OneOf(Set.of("R101", "R100")))),
            "{'type':'hasRole','relationshipType':{'type':'oneOf','terms':['R100','R101']}}"),
        Arguments.of(
            "hasAssociation with a fuzzy target",
            new HasAssociation(
                t,
                new RelationshipType(new Exact("A8")),
                new RelationshipTarget(new Fuzzy("skinn", Fuzziness.ONE))),
            "{'type':'hasAssociation','relationshipType':{'type':'exact','term':'A8'},"
                + "'target':{'match':{'type':'fuzzy','term':'skinn','fuzziness':'1'}}}"),
        Arguments.of(
            "hasInverseAssociation with a contains target",
            new HasInverseAssociation(
                t,
                new RelationshipType(new Exact("A8")),
                new RelationshipTarget(new Contains("Neoplasm"))),
            "{'type':'hasInverseAssociation','relationshipType':{'type':'exact','term':'A8'},"
                + "'target':{'match':{'type':'contains','term':'Neoplasm'}}}"),
        Arguments.of(
            "hasProperty with only a type",
            new HasProperty(t, new Exact("Neoplastic_Status")),
            "{'type':'hasProperty','propertyType':{'type':'exact','term':'Neoplastic_Status'}}"),
        Arguments.of(
            "hasProperty with a value and qualifiers",
            new HasProperty(
                t,
                new Exact("GO_Annotation"),
                new Contains("kinase"),
                new AllQualifiers(
                    List.of(
                        new PropertyQualifier(new Exact("go-evi"), new Exact("TAS")),
                        new NoQualifier(
                            new AnyQualifier(
                                List.of(new PropertyQualifier(new Exact("go-source")))))))),
            "{'type':'hasProperty','propertyType':{'type':'exact','term':'GO_Annotation'},"
                + "'value':{'type':'contains','term':'kinase'},"
                + "'qualifiers':{'type':'allQualifiers','conditions':["
                + "{'type':'qualifier','qualifierType':{'type':'exact','term':'go-evi'},"
                + "'value':{'type':'exact','term':'TAS'}},"
                + "{'type':'noQualifier','condition':{'type':'anyQualifier','conditions':["
                + "{'type':'qualifier','qualifierType':{'type':'exact','term':'go-source'}}]}}]}}"),
        Arguments.of(
            "hasSynonym with a name as given, source, and term type",
            new HasSynonym(t, new Exact("Melanoma"), new Exact("CDISC"), new Exact("PT")),
            "{'type':'hasSynonym','name':{'type':'exact','term':'Melanoma'},"
                + "'source':{'type':'exact','term':'CDISC'},"
                + "'termType':{'type':'exact','term':'PT'}}"),
        Arguments.of(
            "hasSynonym with a search-as-you-type name",
            new HasSynonym(t, new SearchAsYouType("mela")),
            "{'type':'hasSynonym','name':{'type':'searchAsYouType','term':'mela'}}"),
        Arguments.of(
            "inSubset by code",
            new InSubset(t, "C66830"),
            "{'type':'inSubset','subsets':{'match':{'type':'exact','term':'C66830'}}}"));
  }

  /**
   * A query tree writes the expected JSON.
   *
   * @param name the case name
   * @param query the query tree
   * @param json the expected JSON
   * @throws JsonProcessingException if a JSON text isn't valid
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueriesAndJson")
  public void testQueryWritesExpectedJson(
      final String name, final EvsQuery query, final String json) throws JsonProcessingException {
    final String writtenJson = QueryJson.writeQuery(query);
    logger.info("  {}: {}", name, writtenJson);

    assertEquals(getTree(json), getTree(writtenJson));
  }

  /**
   * JSON reads into a tree that writes the same JSON back.
   *
   * @param name the case name
   * @param query the query tree (unused: the JSON is read)
   * @param json the JSON
   * @throws JsonProcessingException if a JSON text isn't valid
   */
  @ParameterizedTest(name = "{0}")
  @MethodSource("getQueriesAndJson")
  public void testJsonReadsBackToSameJson(
      final String name, final EvsQuery query, final String json) throws JsonProcessingException {
    final EvsQuery readQuery = QueryJson.readQuery(getJson(json), TERMINOLOGIES);

    assertEquals(getTree(json), getTree(QueryJson.writeQuery(readQuery)));
  }

  /**
   * Nested nodes get the request's terminologies.
   *
   * @throws JsonProcessingException if a JSON text isn't valid
   */
  @Test
  public void testNestedNodeGetsRequestTerminologies() throws JsonProcessingException {
    final String json = "{'type':'not','child':{'type':'descendantOf','code':'C3262'}}";

    final Not not = (Not) QueryJson.readQuery(getJson(json), TERMINOLOGIES);

    assertEquals(TERMINOLOGIES, not.getChild().getTerminologies());
  }

  /**
   * A fuzzy match without a fuzziness reads as AUTO.
   *
   * @throws JsonProcessingException if a JSON text isn't valid
   */
  @Test
  public void testFuzzyWithoutFuzzinessReadsAsAuto() throws JsonProcessingException {
    final String json = "{'type':'hasRole','relationshipType':{'type':'fuzzy','term':'R101'}}";
    final String autoJson =
        "{'type':'hasRole','relationshipType':{'type':'fuzzy','term':'R101','fuzziness':'AUTO'}}";

    final EvsQuery query = QueryJson.readQuery(getJson(json), TERMINOLOGIES);

    assertEquals(getTree(autoJson), getTree(QueryJson.writeQuery(query)));
  }

  /** An unknown node type fails, naming the type. */
  @Test
  public void testUnknownTypeFailsNamingType() {
    final String json = "{'type':'descendantOff','code':'C3262'}";

    assertReadFailsMentioning(json, "descendantOff");
  }

  /** A JSON object without a type fails. */
  @Test
  public void testMissingTypeFails() {
    final String json = "{'code':'C3262'}";

    assertReadFailsMentioning(json, "type");
  }

  /** An unknown property fails rather than being dropped, naming the property. */
  @Test
  public void testUnknownPropertyFailsNamingProperty() {
    final String json = "{'type':'descendantOf','code':'C3262','maxDepth':2}";

    assertReadFailsMentioning(json, "maxDepth");
  }

  /** Terminologies in the JSON fail: only the request chooses them. */
  @Test
  public void testTerminologiesPropertyFails() {
    final String json = "{'type':'descendantOf','code':'C3262','terminologies':[]}";

    assertReadFailsMentioning(json, "terminologies");
  }

  /** Terminologies in the JSON of a Boolean node fail too. */
  @Test
  public void testTerminologiesPropertyOnBooleanNodeFails() {
    final String json =
        "{'type':'not','child':{'type':'descendantOf','code':'C3262'},'terminologies':[]}";

    assertReadFailsMentioning(json, "terminologies");
  }

  /** A missing required property fails, naming the property. */
  @Test
  public void testMissingRequiredPropertyFailsNamingProperty() {
    final String json = "{'type':'not'}";

    assertReadFailsMentioning(json, "child");
  }

  /** A hierarchy node with both a code and an anchor fails. */
  @Test
  public void testHierarchyWithCodeAndAnchorFails() {
    final String json =
        "{'type':'descendantOf','code':'C3262','anchor':{'type':'descendantOf','code':'C4872'}}";

    assertReadFailsMentioning(json, "exactly one");
  }

  /** A hierarchy node with neither a code nor an anchor fails. */
  @Test
  public void testHierarchyWithoutAnchorFails() {
    final String json = "{'type':'descendantOf','maxLevel':2}";

    assertReadFailsMentioning(json, "exactly one");
  }

  /** A max level below 1 fails with the node's own message. */
  @Test
  public void testMaxLevelBelowOneFailsWithNodeMessage() {
    final String json = "{'type':'descendantOf','code':'C3262','maxLevel':0}";

    assertReadFailsMentioning(json, "maxLevel must be at least");
  }

  /** A relationship target with both a query and a match fails. */
  @Test
  public void testTargetWithQueryAndMatchFails() {
    final String json =
        "{'type':'hasRole','relationshipType':{'type':'exact','term':'R101'},"
            + "'target':{'query':{'type':'descendantOf','code':'C12470'},"
            + "'match':{'type':'exact','term':'Skin'}}}";

    assertReadFailsMentioning(json, "exactly one");
  }

  /** Case-insensitive fuzzy matching fails: OpenSearch has no such query. */
  @Test
  public void testCaseInsensitiveFuzzyFails() {
    final String json =
        "{'type':'hasRole','relationshipType':{'type':'caseInsensitive',"
            + "'match':{'type':'fuzzy','term':'R101'}}}";

    assertReadFailsMentioning(json, "fuzzy");
  }

  /** A node type used where a value match belongs fails. */
  @Test
  public void testQueryWhereMatchBelongsFails() {
    final String json =
        "{'type':'hasRole','relationshipType':{'type':'descendantOf','code':'C3262'}}";

    assertReadFailsMentioning(json, "descendantOf");
  }

  /**
   * Asserts that reading the JSON fails with a message containing the text.
   *
   * @param json the JSON, with single quotes
   * @param text the text the message must contain
   */
  private static void assertReadFailsMentioning(final String json, final String text) {
    final IllegalArgumentException e =
        assertThrows(
            IllegalArgumentException.class,
            () -> QueryJson.readQuery(getJson(json), TERMINOLOGIES));
    logger.info("  {} -> {}", json, e.getMessage());

    assertTrue(e.getMessage().contains(text), e.getMessage());
  }

  /**
   * Returns the JSON tree of a JSON text written with single quotes, or with double quotes.
   *
   * @param json the JSON
   * @return the tree
   * @throws JsonProcessingException if the JSON isn't valid
   */
  private static JsonNode getTree(final String json) throws JsonProcessingException {
    return TREE_READER.readTree(getJson(json));
  }

  /**
   * Returns JSON written with single quotes (easier to read in Java) with double quotes.
   *
   * @param json the JSON with single quotes
   * @return the JSON
   */
  private static String getJson(final String json) {
    return json.replace('\'', '"');
  }

  /**
   * Builds the made-up terminology, with the subset membership association {@link InSubset} needs.
   *
   * @return the terminology
   */
  private static Terminology getTerminology() {
    final Terminology terminology =
        QueryTestTerminologies.getTerminology("ncit", "25.12e", "concept_ncit_2512e");
    final TerminologyMetadata metadata = new TerminologyMetadata();
    metadata.setSubsetMember(Set.of(SUBSET_MEMBERSHIP));
    terminology.setMetadata(metadata);
    return terminology;
  }
}
