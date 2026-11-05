package gov.nih.nci.evs.api.query;

import java.util.Objects;
import java.util.function.UnaryOperator;
import org.opensearch.index.query.QueryBuilder;

/**
 * Runs the wrapped match ignoring case, so "MELANOMA" matches both "Melanoma" and "melanoma". The
 * wrapped match is not changed and remains case sensitive on its own.
 *
 * <p>Only matches that implement {@link SupportsIgnoringCase} can be wrapped. {@link Fuzzy} cannot,
 * because OpenSearch has no case-insensitive fuzzy query.
 */
public class CaseInsensitive implements ValueMatch {

  /** The match to run ignoring case. */
  private SupportsIgnoringCase match;

  /** Instantiates an empty {@link CaseInsensitive}. */
  public CaseInsensitive() {
    // n/a
  }

  /**
   * Instantiates a {@link CaseInsensitive}.
   *
   * @param match the match to run ignoring case
   */
  public CaseInsensitive(final SupportsIgnoringCase match) {
    this.match = Objects.requireNonNull(match, "match");
  }

  /**
   * Returns the match to run ignoring case.
   *
   * @return the match
   */
  public SupportsIgnoringCase getMatch() {
    return match;
  }

  /**
   * Sets the match to run ignoring case.
   *
   * @param match the match to run ignoring case
   */
  public void setMatch(final SupportsIgnoringCase match) {
    this.match = match;
  }

  /* see superclass */
  @Override
  public CaseInsensitive getWithTermsMapped(final UnaryOperator<String> termMapper) {
    return new CaseInsensitive(match.getWithTermsMapped(termMapper));
  }

  /* see superclass */
  @Override
  public QueryBuilder getQueryBuilder(final String field) {
    return match.getCaseInsensitiveQueryBuilder(field);
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

    final CaseInsensitive other = (CaseInsensitive) obj;
    return Objects.equals(match, other.match);
  }

  /* see superclass */
  @Override
  public int hashCode() {
    return Objects.hash(match);
  }
}
