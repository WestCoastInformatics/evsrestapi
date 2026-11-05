package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Tests the Typeahead node, which completes an incomplete term to the best matching concepts,
 * through the query endpoint.
 */
public class TypeaheadEndpointTest extends AbstractQueryEndpointTest {

  /** The typeahead for the term "melanoma". */
  private static final String MELANOMA_TYPEAHEAD_QUERY =
      """
      {"type": "Typeahead", "incompleteTerm": "melanoma"}
      """;

  /**
   * Test Typeahead returns Melanoma first for "melanoma".
   *
   * @throws Exception the exception
   */
  @Test
  public void testTypeaheadReturnsBestMatchFirst() throws Exception {
    assertThat(fetchCodes(MELANOMA_TYPEAHEAD_QUERY)).first().isEqualTo(MELANOMA);
  }

  /**
   * Test Typeahead with a misspelled property (term, not incompleteTerm) is rejected as a bad
   * request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testTypeaheadWithMisspelledPropertyReturnsBadRequest() throws Exception {
    final String termInsteadOfIncompleteTermQuery =
        """
        {"type": "Typeahead", "term": "melanoma"}
        """;

    postQuery(termInsteadOfIncompleteTermQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test Typeahead round trips through Jackson unchanged.
   *
   * @throws Exception the exception
   */
  @Test
  public void testTypeaheadRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(MELANOMA_TYPEAHEAD_QUERY);
  }
}
