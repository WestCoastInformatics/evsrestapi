package gov.nih.nci.evs.api.query;

import java.util.function.UnaryOperator;
import org.opensearch.index.query.QueryBuilder;

/**
 * How a value is matched, e.g. exactly, by prefix, or within a few edits, without saying which
 * field holds the value. {@link FieldMatch} applies a match to a concept field. A node that owns
 * its own fields applies it to those, e.g. a relationship's type to the entry's type name and code.
 *
 * <p>Matches compare against the whole indexed value, so they are intended for keyword fields.
 * Against an analyzed text field they operate on individual lowercased tokens instead.
 */
public interface ValueMatch {

  /**
   * Returns the OpenSearch query matching this against the field.
   *
   * @param field the field holding the value
   * @return the query builder
   */
  QueryBuilder getQueryBuilder(String field);

  /**
   * Returns this match with each term it compares passed through the mapper, e.g. normalized the
   * way a field's values were when they were indexed. The kind of match doesn't change.
   *
   * @param termMapper the function applied to each term
   * @return the match with mapped terms
   */
  ValueMatch getWithTermsMapped(UnaryOperator<String> termMapper);
}
