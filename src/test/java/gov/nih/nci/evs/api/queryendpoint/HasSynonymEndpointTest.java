package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import gov.nih.nci.evs.api.query.CaseInsensitive;
import gov.nih.nci.evs.api.query.Exact;
import gov.nih.nci.evs.api.query.HasSynonym;
import org.junit.jupiter.api.Test;

/**
 * Tests the HasSynonym node, which matches concepts with a synonym matching the given name, source,
 * and term type, through the query endpoint.
 */
public class HasSynonymEndpointTest extends AbstractQueryEndpointTest {

  /** The NCI preferred term "melanoma", with the source matched ignoring case. */
  private static final String MELANOMA_PREFERRED_TERM_QUERY =
      """
      {"type": "HasSynonym",
       "normalizedNameMatch": {"type": "Exact", "term": "melanoma"},
       "sourceMatch": {"type": "CaseInsensitive", "match": {"type": "Exact", "term": "nci"}},
       "termTypeMatch": {"type": "Exact", "term": "PT"}}
      """;

  /**
   * Test HasSynonym returns the concepts with a matching synonym: the NCI preferred term
   * "melanoma", with the source matched ignoring case.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasSynonymReturnsMatchingConcepts() throws Exception {
    assertThat(fetchCodes(MELANOMA_PREFERRED_TERM_QUERY)).containsExactly(MELANOMA);
  }

  /**
   * Test HasSynonym with a case-insensitive fuzzy match, which OpenSearch can't run, is rejected as
   * a bad request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasSynonymWithCaseInsensitiveFuzzyReturnsBadRequest() throws Exception {
    final String caseInsensitiveFuzzyQuery =
        """
        {"type": "HasSynonym",
         "normalizedNameMatch":
           {"type": "CaseInsensitive", "match": {"type": "Fuzzy", "term": "melanoma"}}}
        """;

    postQuery(caseInsensitiveFuzzyQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test HasSynonym round trips through Jackson unchanged.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasSynonymRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(MELANOMA_PREFERRED_TERM_QUERY);
  }

  /**
   * Test HasSynonym serializes to the expected JSON, the wire format clients use.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasSynonymSerializesToExpectedJson() throws Exception {
    final HasSynonym melanomaPreferredTerm =
        new HasSynonym(
            new Exact("melanoma"), new CaseInsensitive(new Exact("nci")), new Exact("PT"));

    assertSerializesTo(melanomaPreferredTerm, MELANOMA_PREFERRED_TERM_QUERY);
  }
}
