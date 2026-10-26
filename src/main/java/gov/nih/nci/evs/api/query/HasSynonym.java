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
 * terms are normalized the same way first, so {@code Exact("MELANOMA, MALIGNANT")} finds the
 * synonym "Melanoma, Malignant". Matching therefore ignores case and punctuation, and a term's
 * surrounding spaces are trimmed (so {@code StartsWith("lung ")} is {@code StartsWith("lung")}).
 * Source, term type, and sub-source are compared as given.
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

  /** The match for the synonym's name, with normalized terms, or null to match any name. */
  private final ValueMatch normalizedNameMatch;

  /** The match for the synonym's source, or null to match any source. */
  private final ValueMatch sourceMatch;

  /** The match for the synonym's term type, or null to match any term type. */
  private final ValueMatch termTypeMatch;

  /** The match for the synonym's sub-source, or null to match any sub-source. */
  private final ValueMatch subSourceMatch;

  /**
   * Instantiates a {@link HasSynonym} that matches a synonym with a matching name, from any source.
   *
   * @param terminologies the terminologies being searched
   * @param nameMatch the match for the synonym's name
   */
  public HasSynonym(final List<Terminology> terminologies, final ValueMatch nameMatch) {
    this(terminologies, Objects.requireNonNull(nameMatch, "nameMatch"), null, null, null);
  }

  /**
   * Instantiates a {@link HasSynonym} that matches a synonym with a matching name, source, and term
   * type, from any sub-source.
   *
   * @param terminologies the terminologies being searched
   * @param nameMatch the match for the synonym's name, or null for any name
   * @param sourceMatch the match for the synonym's source, or null for any source
   * @param termTypeMatch the match for the synonym's term type, or null for any term type
   */
  public HasSynonym(
      final List<Terminology> terminologies,
      final ValueMatch nameMatch,
      final ValueMatch sourceMatch,
      final ValueMatch termTypeMatch) {
    this(terminologies, nameMatch, sourceMatch, termTypeMatch, null);
  }

  /**
   * Instantiates a {@link HasSynonym} that matches a synonym with a matching name, source, term
   * type, and sub-source.
   *
   * @param terminologies the terminologies being searched
   * @param nameMatch the match for the synonym's name, or null for any name
   * @param sourceMatch the match for the synonym's source, or null for any source
   * @param termTypeMatch the match for the synonym's term type, or null for any term type
   * @param subSourceMatch the match for the synonym's sub-source, or null for any sub-source
   */
  public HasSynonym(
      final List<Terminology> terminologies,
      final ValueMatch nameMatch,
      final ValueMatch sourceMatch,
      final ValueMatch termTypeMatch,
      final ValueMatch subSourceMatch) {
    super(terminologies);
    final boolean hasNameMatch = nameMatch != null;
    this.normalizedNameMatch =
        hasNameMatch ? nameMatch.getWithTermsMapped(ConceptUtils::normalize) : null;
    this.sourceMatch = sourceMatch;
    this.termTypeMatch = termTypeMatch;
    this.subSourceMatch = subSourceMatch;
  }

  /* see superclass */
  @Override
  public QueryBuilder fetchOpenSearchQuery(final OpenSearchOperations operations) {
    final BoolQueryBuilder entryConditions = QueryBuilders.boolQuery();
    addFieldCondition(entryConditions, NORMALIZED_NAME_FIELD, normalizedNameMatch, operations);
    addFieldCondition(entryConditions, SOURCE_FIELD, sourceMatch, operations);
    addFieldCondition(entryConditions, TERM_TYPE_FIELD, termTypeMatch, operations);
    addFieldCondition(entryConditions, SUB_SOURCE_FIELD, subSourceMatch, operations);

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
   */
  private void addFieldCondition(
      final BoolQueryBuilder entryConditions,
      final String field,
      final ValueMatch match,
      final OpenSearchOperations operations) {
    final boolean hasMatch = match != null;
    if (hasMatch) {
      entryConditions.filter(
          new FieldMatch(terminologies, field, match).fetchOpenSearchQuery(operations));
    }
  }
}
