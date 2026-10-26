package gov.nih.nci.evs.api.query;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;
import java.util.function.UnaryOperator;
import org.junit.jupiter.api.Test;
import org.opensearch.common.unit.Fuzziness;

/**
 * Tests {@link ValueMatch#getWithTermsMapped}: each match maps its terms and keeps its kind and any
 * other settings. Value matches are records, so equal matches compare equal. Needs no services.
 */
public class ValueMatchTermMappingTest {

  /** Maps every term to upper case, so a mapped term is easy to recognize. */
  private static final UnaryOperator<String> TO_UPPER_CASE = String::toUpperCase;

  /** Exact maps its term. */
  @Test
  public void testExactMapsTerm() {
    assertEquals(new Exact("MELANOMA"), new Exact("melanoma").getWithTermsMapped(TO_UPPER_CASE));
  }

  /** StartsWith maps its term. */
  @Test
  public void testStartsWithMapsTerm() {
    assertEquals(
        new StartsWith("MELAN"), new StartsWith("melan").getWithTermsMapped(TO_UPPER_CASE));
  }

  /** Contains maps its term. */
  @Test
  public void testContainsMapsTerm() {
    assertEquals(new Contains("ANOM"), new Contains("anom").getWithTermsMapped(TO_UPPER_CASE));
  }

  /** Fuzzy maps its term and keeps its fuzziness. */
  @Test
  public void testFuzzyMapsTermAndKeepsFuzziness() {
    assertEquals(
        new Fuzzy("MELANOMA", Fuzziness.ONE),
        new Fuzzy("melanoma", Fuzziness.ONE).getWithTermsMapped(TO_UPPER_CASE));
  }

  /** OneOf maps every term. */
  @Test
  public void testOneOfMapsEveryTerm() {
    assertEquals(
        new OneOf(Set.of("LUNG", "SKIN")),
        new OneOf(Set.of("lung", "skin")).getWithTermsMapped(TO_UPPER_CASE));
  }

  /** SearchAsYouType maps its term. */
  @Test
  public void testSearchAsYouTypeMapsTerm() {
    assertEquals(
        new SearchAsYouType("LUNG CA"),
        new SearchAsYouType("lung ca").getWithTermsMapped(TO_UPPER_CASE));
  }

  /** CaseInsensitive maps the wrapped match's term and stays case insensitive. */
  @Test
  public void testCaseInsensitiveMapsWrappedTerm() {
    assertEquals(
        new CaseInsensitive(new Exact("MELANOMA")),
        new CaseInsensitive(new Exact("melanoma")).getWithTermsMapped(TO_UPPER_CASE));
  }
}
