package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.util.ConceptUtils;
import java.util.List;
import java.util.Objects;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches concepts that have a synonym with a matching name, optionally from a given source, term
 * type, and sub-source, e.g. concepts with a CDISC preferred term (PT) containing "melanoma". The
 * name, source, term type, and sub-source must all match on the same synonym entry: a concept with
 * a CDISC synonym and a different PT synonym doesn't match "CDISC PT".
 *
 * <p>NCIt models a synonym as a FULL_SYN property whose source and term type are qualifiers. The
 * loader remodels them into the concept's {@code synonyms} list, with the qualifiers as fields, so
 * this node searches that list rather than {@link HasProperty}'s {@code properties}.
 *
 * <p>The name is compared to the synonym's normalized name: lowercased, with punctuation turned
 * into spaces and leading punctuation removed ({@link ConceptUtils#normalize}). The name match's
 * terms are normalized the same way when the query is built, so {@code Exact("MELANOMA,
 * MALIGNANT")} finds the synonym "Melanoma, Malignant". Matching therefore ignores case and
 * punctuation, and a term's surrounding spaces are trimmed (so {@code StartsWith("lung ")} is
 * {@code StartsWith("lung")}). Source, term type, and sub-source are compared as given.
 *
 * <p>Combine synonym nodes with the concept-level {@code And}/{@code Or}/{@code Not}: {@code Or} of
 * two nodes is a synonym matching either, {@code And} is a concept having both (possibly on
 * different synonyms), and {@code Not} is a concept with no matching synonym. Conditions that must
 * hold together on one synonym belong in one node. OR or NOT between fields of one synonym (e.g. "a
 * CDISC synonym that isn't a PT") isn't supported yet: see {@link QualifierCondition} for how that
 * would be modeled.
 */
public final class HasSynonym extends EvsQuery {

  /** The concept document's list of synonyms, mapped as nested so synonyms can be searched. */
  private static final String SYNONYMS_FIELD = "synonyms";

  /** A synonym entry's normalized name, e.g. "melanoma malignant". */
  private static final String NORMALIZED_NAME_FIELD = SYNONYMS_FIELD + ".normName";

  /** A synonym entry's source, e.g. "CDISC". */
  private static final String SOURCE_FIELD = SYNONYMS_FIELD + ".source";

  /** A synonym entry's term type, e.g. "PT". */
  private static final String TERM_TYPE_FIELD = SYNONYMS_FIELD + ".termType";

  /** A synonym entry's sub-source, e.g. "NCI-GLOSS". */
  private static final String SUB_SOURCE_FIELD = SYNONYMS_FIELD + ".subSource";

  /**
   * The match for the synonym's name, as given, or null to match any name. Its terms are normalized
   * when the query is built, so clients can send the name as written.
   */
  private ValueMatch name;

  /** The match for the synonym's source, or null to match any source. */
  private ValueMatch source;

  /** The match for the synonym's term type, or null to match any term type. */
  private ValueMatch termType;

  /** The match for the synonym's sub-source, or null to match any sub-source. */
  private ValueMatch subSource;

  /** Instantiates an empty {@link HasSynonym}. */
  public HasSynonym() {
    // n/a
  }

  /**
   * Instantiates a {@link HasSynonym} that matches a synonym with a matching name, from any source.
   *
   * @param name the match for the synonym's name
   */
  public HasSynonym(final ValueMatch name) {
    this(Objects.requireNonNull(name, "name"), null, null, null);
  }

  /**
   * Instantiates a {@link HasSynonym} that matches a synonym with a matching name, source, and term
   * type, from any sub-source.
   *
   * @param name the match for the synonym's name, or null for any name
   * @param source the match for the synonym's source, or null for any source
   * @param termType the match for the synonym's term type, or null for any term type
   */
  public HasSynonym(final ValueMatch name, final ValueMatch source, final ValueMatch termType) {
    this(name, source, termType, null);
  }

  /**
   * Instantiates a {@link HasSynonym} that matches a synonym with a matching name, source, term
   * type, and sub-source.
   *
   * @param name the match for the synonym's name, or null for any name
   * @param source the match for the synonym's source, or null for any source
   * @param termType the match for the synonym's term type, or null for any term type
   * @param subSource the match for the synonym's sub-source, or null for any sub-source
   */
  public HasSynonym(
      final ValueMatch name,
      final ValueMatch source,
      final ValueMatch termType,
      final ValueMatch subSource) {
    this.name = name;
    this.source = source;
    this.termType = termType;
    this.subSource = subSource;
  }

  /**
   * Returns the match for the synonym's name, as given.
   *
   * @return the name match, or null to match any name
   */
  public ValueMatch getName() {
    return name;
  }

  /**
   * Sets the match for the synonym's name, as given.
   *
   * @param name the name match, or null to match any name
   */
  public void setName(final ValueMatch name) {
    this.name = name;
  }

  /**
   * Returns the match for the synonym's source.
   *
   * @return the source match, or null to match any source
   */
  public ValueMatch getSource() {
    return source;
  }

  /**
   * Sets the match for the synonym's source.
   *
   * @param source the source match, or null to match any source
   */
  public void setSource(final ValueMatch source) {
    this.source = source;
  }

  /**
   * Returns the match for the synonym's term type.
   *
   * @return the term type match, or null to match any term type
   */
  public ValueMatch getTermType() {
    return termType;
  }

  /**
   * Sets the match for the synonym's term type.
   *
   * @param termType the term type match, or null to match any term type
   */
  public void setTermType(final ValueMatch termType) {
    this.termType = termType;
  }

  /**
   * Returns the match for the synonym's sub-source.
   *
   * @return the sub-source match, or null to match any sub-source
   */
  public ValueMatch getSubSource() {
    return subSource;
  }

  /**
   * Sets the match for the synonym's sub-source.
   *
   * @param subSource the sub-source match, or null to match any sub-source
   */
  public void setSubSource(final ValueMatch subSource) {
    this.subSource = subSource;
  }

  /**
   * Returns the name match with its terms normalized the way synonym names were when they were
   * indexed, to compare against the normalized name field.
   *
   * @return the normalized name match, or null to match any name
   */
  private ValueMatch getNormalizedName() {
    final boolean hasName = name != null;
    return hasName ? name.getWithTermsMapped(ConceptUtils::normalize) : null;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(
      final OpenSearchOperations operations, final List<Terminology> terminologies) {
    final BoolQueryBuilder entryConditions = QueryBuilders.boolQuery();
    addFieldCondition(
        entryConditions, NORMALIZED_NAME_FIELD, getNormalizedName(), operations, terminologies);
    addFieldCondition(entryConditions, SOURCE_FIELD, source, operations, terminologies);
    addFieldCondition(entryConditions, TERM_TYPE_FIELD, termType, operations, terminologies);
    addFieldCondition(entryConditions, SUB_SOURCE_FIELD, subSource, operations, terminologies);

    return QueryBuilders.nestedQuery(SYNONYMS_FIELD, entryConditions, ScoreMode.None);
  }

  /**
   * Requires the synonym entry's field to match, when there is a match for it. With no conditions
   * at all, the nested query matches concepts with any synonym.
   *
   * @param entryConditions the conditions one synonym entry must meet, added to
   * @param field the synonym entry's field
   * @param match the match for the field, or null to leave the field unconstrained
   * @param operations the OpenSearch operations
   * @param terminologies the terminologies being searched
   */
  private static void addFieldCondition(
      final BoolQueryBuilder entryConditions,
      final String field,
      final ValueMatch match,
      final OpenSearchOperations operations,
      final List<Terminology> terminologies) {
    final boolean hasMatch = match != null;
    if (hasMatch) {
      entryConditions.filter(
          new FieldMatch(field, match).fetchOpenSearchQuery(operations, terminologies));
    }
  }
}
