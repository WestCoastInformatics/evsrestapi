package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import java.util.Objects;
import org.apache.lucene.search.join.ScoreMode;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.BoolQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilders;

/**
 * Matches concepts with a relationship of the given type in one of their relationship lists,
 * optionally pointing to the given target, e.g. concepts whose primary anatomic site role points to
 * Skin. Roles and associations are both lists of entries pairing a type with a related concept, so
 * one node searches either: only the list differs. Each subclass says which list it searches
 * ({@link #getRelationshipListFieldName}): {@link HasRole} searches {@code roles}, {@link
 * HasAssociation} searches {@code associations}, and {@link HasInverseAssociation} searches {@code
 * inverseAssociations}.
 *
 * <p>The type and the target are each a {@link ValueMatch} on the entry's name or code, either one
 * matching: {@code new HasRole(new StartsWith("Disease_Has_Primary"), new Exact("C12470"))}. Wrap a
 * match in {@link CaseInsensitive} to ignore case. Both are compared against values stored on the
 * entry itself, so no other search runs. The target name is analyzed text, so a match compares its
 * individual lowercased words, not the whole name.
 *
 * <p>The type and target must match on the same entry. A concept with "primary site: Lung" and
 * "associated site: Skin" does not match "primary site: Skin".
 *
 * <p>Needs concept indexes mapped with the list as nested. On an index loaded before that mapping,
 * the search fails instead of silently returning nothing.
 */
public abstract class HasRelationship extends EvsQuery {

  /** The entry field holding the type's name, e.g. "Disease_Has_Primary_Anatomic_Site". */
  private static final String TYPE_NAME_FIELD = "type";

  /** The entry field holding the type's code, e.g. "R101". */
  private static final String TYPE_CODE_FIELD = "code";

  /** The entry field holding the name of the concept the entry points to. Analyzed text. */
  private static final String TARGET_NAME_FIELD = "relatedName";

  /** The entry field holding the code of the concept the entry points to. */
  private static final String TARGET_CODE_FIELD = "relatedCode";

  /** Joins the list's name and an entry field into the entry field's full name. */
  private static final String ENTRY_FIELD_SEPARATOR = ".";

  /**
   * How the relationship's type name or code must match. The type is the kind of relationship the
   * entry expresses, e.g. "Disease_Has_Primary_Anatomic_Site" (code "R101"). A concept has many
   * entries, each pairing a type with a target concept, and the type says how the concept relates
   * to that target.
   */
  private final ValueMatch typeNameOrCodeMatch;

  /** How the name or code of the concept the relationship points to must match, or null for any. */
  private final ValueMatch targetNameOrCodeMatch;

  /**
   * Instantiates a {@link HasRelationship} that matches a relationship of the given type, pointing
   * to any concept.
   *
   * @param typeNameOrCodeMatch how the relationship's type name or code must match
   */
  protected HasRelationship(final ValueMatch typeNameOrCodeMatch) {
    this.typeNameOrCodeMatch = Objects.requireNonNull(typeNameOrCodeMatch, "typeNameOrCodeMatch");
    this.targetNameOrCodeMatch = null;
  }

  /**
   * Instantiates a {@link HasRelationship} that matches a relationship of the given type pointing
   * to the target.
   *
   * @param typeNameOrCodeMatch how the relationship's type name or code must match
   * @param targetNameOrCodeMatch how the target's name or code must match
   */
  protected HasRelationship(
      final ValueMatch typeNameOrCodeMatch, final ValueMatch targetNameOrCodeMatch) {
    this.typeNameOrCodeMatch = Objects.requireNonNull(typeNameOrCodeMatch, "typeNameOrCodeMatch");
    this.targetNameOrCodeMatch =
        Objects.requireNonNull(targetNameOrCodeMatch, "targetNameOrCodeMatch");
  }

  /**
   * Returns the name of the concept document's list of relationships this node searches, e.g.
   * {@code roles}. OpenSearch calls it the nested path.
   *
   * <p>The list must be mapped as nested in {@code Concept}, or the search fails.
   *
   * @return the list's field name
   */
  protected abstract String getRelationshipListFieldName();

  /**
   * Returns how the relationship's type name or code must match.
   *
   * @return the type name or code match
   */
  public ValueMatch getTypeNameOrCodeMatch() {
    return typeNameOrCodeMatch;
  }

  /**
   * Indicates whether the relationship must point to a specific target.
   *
   * @return whether there is a target
   */
  private boolean getHasTargetNameOrCodeMatch() {
    return targetNameOrCodeMatch != null;
  }

  /**
   * Returns how the target's name or code must match.
   *
   * @return the target name or code match, or null for any target
   */
  public ValueMatch getTargetNameOrCodeMatch() {
    return targetNameOrCodeMatch;
  }

  /**
   * Returns the OpenSearch query for this node: a concept matches when one of the entries in its
   * relationship list has this type and, if there is one, points to the target.
   *
   * @param operations the OpenSearch operations, for building the field matches
   * @param terminologies the terminologies being searched
   * @return the query builder
   */
  @Override
  public QueryBuilder fetchOpenSearchQuery(
      final OpenSearchOperations operations, final List<Terminology> terminologies) {
    final BoolQueryBuilder entryConditions =
        QueryBuilders.boolQuery()
            .must(
                getEntryNameOrCodeQuery(
                    TYPE_NAME_FIELD,
                    TYPE_CODE_FIELD,
                    typeNameOrCodeMatch,
                    operations,
                    terminologies));

    final boolean hasTarget = getHasTargetNameOrCodeMatch();
    if (hasTarget) {
      entryConditions.must(
          getEntryNameOrCodeQuery(
              TARGET_NAME_FIELD,
              TARGET_CODE_FIELD,
              targetNameOrCodeMatch,
              operations,
              terminologies));
    }

    return getConceptsWithAnyEntryMatching(entryConditions);
  }

  /**
   * Returns the condition matching an entry whose name field or code field matches.
   *
   * @param nameEntryField the entry field holding the name
   * @param codeEntryField the entry field holding the code
   * @param nameOrCodeMatch how the name or code must match
   * @param operations the OpenSearch operations, for building the field matches
   * @param terminologies the terminologies being searched
   * @return the condition
   */
  private QueryBuilder getEntryNameOrCodeQuery(
      final String nameEntryField,
      final String codeEntryField,
      final ValueMatch nameOrCodeMatch,
      final OpenSearchOperations operations,
      final List<Terminology> terminologies) {
    return new NameOrCodeMatch(
            getEntryFieldName(nameEntryField), getEntryFieldName(codeEntryField), nameOrCodeMatch)
        .fetchOpenSearchQuery(operations, terminologies);
  }

  /**
   * Returns the full name of a field on this node's entries: the list's name, a dot, and the field,
   * e.g. {@code roles.relatedCode}.
   *
   * @param entryField the field on one entry, e.g. {@code relatedCode}
   * @return the full field name
   */
  private String getEntryFieldName(final String entryField) {
    return getRelationshipListFieldName() + ENTRY_FIELD_SEPARATOR + entryField;
  }

  /**
   * Builds a query matching concepts that have at least one entry in this node's relationship list
   * satisfying all the given conditions. An entry is one item in the list, pairing a type with a
   * target.
   *
   * <p>The conditions must hold on the same entry, which is why this is a {@code nested} query.
   * Without it, OpenSearch flattens every entry's fields together, and "Lung Carcinoma" (primary
   * site Lung, associated site Skin) would match "primary site: Skin" by taking the type from one
   * entry and the target from the other.
   *
   * @param entryConditions the conditions one entry must meet
   * @return the query builder
   */
  private QueryBuilder getConceptsWithAnyEntryMatching(final QueryBuilder entryConditions) {
    return QueryBuilders.nestedQuery(
        getRelationshipListFieldName(), entryConditions, ScoreMode.None);
  }
}
