package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.common.unit.Fuzziness;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches values within a small edit distance of the term (case sensitive). By default the allowed
 * distance scales with term length: exact for 1-2 characters, 1 edit for 3-5, 2 edits beyond.
 */
public class Fuzzy implements ValueMatch {

  /** The term the value must approximately equal. */
  private String term;

  /** The allowed edit distance. */
  private Fuzziness fuzziness;

  /** Instantiates an empty {@link Fuzzy}. */
  public Fuzzy() {
    // n/a
  }

  /**
   * Instantiates a {@link Fuzzy} with an explicit edit distance.
   *
   * @param term the term the value must approximately equal
   * @param fuzziness the allowed edit distance
   */
  public Fuzzy(final String term, final Fuzziness fuzziness) {
    this.term = Objects.requireNonNull(term, "term");
    this.fuzziness = Objects.requireNonNull(fuzziness, "fuzziness");
  }

  /**
   * Instantiates a {@link Fuzzy} with an edit distance scaled to the term length.
   *
   * @param term the term the value must approximately equal
   */
  public Fuzzy(final String term) {
    this(term, Fuzziness.AUTO);
  }

  /**
   * Returns the term the value must approximately equal.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }

  /**
   * Sets the term the value must approximately equal.
   *
   * @param term the term the value must approximately equal
   */
  public void setTerm(final String term) {
    this.term = term;
  }

  /**
   * Returns the allowed edit distance.
   *
   * @return the fuzziness
   */
  public Fuzziness getFuzziness() {
    return fuzziness;
  }

  /**
   * Sets the allowed edit distance.
   *
   * @param fuzziness the allowed edit distance
   */
  public void setFuzziness(final Fuzziness fuzziness) {
    this.fuzziness = fuzziness;
  }

  /* see superclass */
  @Override
  public Fuzzy getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new Fuzzy(termMapper.apply(term), fuzziness);
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return QueryBuilders.fuzzyQuery(field, term).fuzziness(fuzziness);
  }

  /* see superclass */
  @Override
  public boolean equals(final Object obj) {
    if (this == obj) {
      return true;
    }

    final boolean isSameClass = obj != null && getClass() == obj.getClass();
    if (!isSameClass) {
      return false;
    }

    final Fuzzy other = (Fuzzy) obj;
    return Objects.equals(term, other.term) && Objects.equals(fuzziness, other.fuzziness);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(term, fuzziness);
  }
}
