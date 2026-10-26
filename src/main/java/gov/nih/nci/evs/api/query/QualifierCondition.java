package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;
import org.opensearch.data.core.OpenSearchOperations;
import org.opensearch.index.query.QueryBuilder;

/**
 * A condition on one property entry's qualifiers, e.g. "go-id is GO:0005737 and go-evi is TAS or
 * IDA". {@link HasProperty} checks it inside its query on each property entry, so every part of the
 * condition holds on the same entry. The leaf is {@link PropertyQualifier}; {@link AllQualifiers},
 * {@link AnyQualifier}, and {@link NoQualifier} combine conditions.
 *
 * <p><b>Why qualifiers have their own logical operators.</b> The concept-level {@code And}, {@code
 * Or}, and {@code Not} can't combine qualifiers: they combine sets of concepts, and once a {@link
 * HasProperty} is reduced to the concepts it matches, which property entry matched is lost. {@code
 * And} of "has a GO annotation with go-id GO:0005737" and "has a GO annotation with go-evi ND"
 * finds genes where those are two different annotations (9 genes in NCIt 26.01a), while a single
 * annotation with both finds none. The same {@code bool} clauses are correct only inside the
 * property's nested query, which is what these operators build.
 *
 * <p><b>Alternative considered: one generic set of logical nodes.</b> {@code And}, {@code Or}, and
 * {@code Not} could instead be generic over the scope a condition is evaluated in, e.g. {@code
 * Condition<ConceptScope>} for concept queries and {@code Condition<QualifierScope>} for this, so
 * one {@code And<S>} serves both and the type parameter still stops a concept query (e.g. {@code
 * DescendantOf}) from being put inside a qualifier condition. It wasn't chosen yet because {@link
 * EvsQuery} also owns the terminologies, index coordinates, and dependency queries, so making the
 * logical nodes generic means refactoring every node, on top of logical nodes that are still
 * exploratory. Revisit it when a second entry-level scope needs logic, e.g. a synonym's source and
 * type: these classes then become {@code And<QualifierScope>} and so on.
 */
public interface QualifierCondition {

  /**
   * Returns a query matching property entries whose qualifiers meet this condition. Runs inside the
   * query over the property entries.
   *
   * @param terminologies the terminologies being searched
   * @param qualifierListField the property entry's list of qualifiers, mapped as nested
   * @param operations the OpenSearch operations
   * @return the query
   */
  QueryBuilder getPropertiesMatching(
      List<Terminology> terminologies, String qualifierListField, OpenSearchOperations operations);
}
