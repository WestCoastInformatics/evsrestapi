package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;

/**
 * A leaf query that matches a term against a single field using one match strategy.
 *
 * <p>Atomic queries compare against the whole indexed value, so they are intended for keyword
 * fields. Against an analyzed text field they operate on individual lowercased tokens instead.
 */
public abstract class AtomicQuery extends EvsQuery {

  /** The field to match against. */
  protected final String field;

  /** The term to match. */
  protected final String term;

  /**
   * Instantiates an {@link AtomicQuery}.
   *
   * @param terminologies the terminologies being searched
   * @param field the field to match against
   * @param term the term to match
   */
  protected AtomicQuery(
      final List<Terminology> terminologies, final String field, final String term) {
    super(terminologies);
    this.field = Objects.requireNonNull(field, "field");
    this.term = Objects.requireNonNull(term, "term");
  }

  /**
   * Returns the field to match against.
   *
   * @return the field
   */
  public String getField() {
    return field;
  }

  /**
   * Returns the term to match.
   *
   * @return the term
   */
  public String getTerm() {
    return term;
  }
}
