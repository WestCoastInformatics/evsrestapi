package gov.nih.nci.evs.api.queryendpoint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

/**
 * Tests HasProperty, which matches the concepts with a property entry matching its type, value and
 * qualifiers, through the query endpoint.
 */
public class HasPropertyEndpointTest extends AbstractQueryEndpointTest {

  /**
   * The nucleotide-excision repair GO annotations with go-id GO:0006289 whose evidence is neither
   * TAS nor NR.
   */
  private static final String REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY =
      """
      {"type": "HasProperty",
       "typeNameOrCodeMatch": {"type": "Exact", "term": "P211"},
       "valueMatch": {"type": "Exact", "term": "nucleotide-excision repair"},
       "qualifierCondition": {"type": "AllQualifiers", "conditions": [
         {"type": "PropertyQualifier",
          "typeNameOrCodeMatch": {"type": "Exact", "term": "P387"},
          "valueMatch": {"type": "OneOf", "terms": ["GO:0006289"]}},
         {"type": "NoQualifier", "condition": {"type": "AnyQualifier", "conditions": [
           {"type": "PropertyQualifier",
            "typeNameOrCodeMatch": {"type": "Exact", "term": "P389"},
            "valueMatch": {"type": "Exact", "term": "TAS"}},
           {"type": "PropertyQualifier",
            "typeNameOrCodeMatch": {"type": "Exact", "term": "P389"},
            "valueMatch": {"type": "Exact", "term": "NR"}}]}}]}}
      """;

  /**
   * Test HasProperty with qualifiers returns the concepts with a matching property entry: the
   * nucleotide-excision repair GO annotations with go-id GO:0006289 whose evidence is neither TAS
   * nor NR. Two such annotations have NAS evidence; the other five, with TAS or NR, are left out.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertyWithQualifiersReturnsMatchingConcepts() throws Exception {
    // ERCC2 Gene and ERCC2 wt Allele
    assertThat(fetchCodes(REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY))
        .containsExactlyInAnyOrder("C20657", "C50981");
  }

  /**
   * Test HasProperty with a misspelled qualifier type (AllQualifier) is rejected as a bad request.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertyWithMisspelledQualifierTypeReturnsBadRequest() throws Exception {
    final String misspelledQualifierTypeQuery =
        """
        {"type": "HasProperty",
         "typeNameOrCodeMatch": {"type": "Exact", "term": "P211"},
         "qualifierCondition": {"type": "AllQualifier", "conditions": []}}
        """;

    postQuery(misspelledQualifierTypeQuery).andExpect(status().isBadRequest());
  }

  /**
   * Test HasProperty round trips unchanged through Jackson.
   *
   * @throws Exception the exception
   */
  @Test
  public void testHasPropertyRoundTripsUnchanged() throws Exception {
    assertRoundTripsUnchanged(REPAIR_ANNOTATION_WITHOUT_TAS_OR_NR_QUERY);
  }
}
