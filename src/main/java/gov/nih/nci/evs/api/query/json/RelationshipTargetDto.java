package gov.nih.nci.evs.api.query.json;

import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.query.RelationshipTarget;
import gov.nih.nci.evs.api.query.RelationshipType;
import java.util.List;

/**
 * The JSON form of a {@link RelationshipTarget}: either a {@code query} selecting the target
 * concepts, or a {@code match} on the entry's target name or code. Exactly one is given.
 *
 * @param query the query selecting the target concepts, or null for a match
 * @param match how the entry's target name or code must match, or null for a query
 */
public record RelationshipTargetDto(QueryDto query, ValueMatchDto match) {

  /**
   * Checks that exactly one of the query and match is given.
   *
   * @throws IllegalArgumentException if both or neither are given
   */
  public RelationshipTargetDto {
    final boolean hasOneForm = (query == null) != (match == null);
    if (!hasOneForm) {
      throw new IllegalArgumentException(
          "A relationship target needs exactly one of 'query' or 'match'");
    }
  }

  /**
   * Builds the relationship target.
   *
   * @param terminologies the terminologies being searched, for a target query
   * @return the target
   */
  public RelationshipTarget toTarget(final List<Terminology> terminologies) {
    final boolean hasQuery = query != null;
    return hasQuery
        ? new RelationshipTarget(query.toQuery(terminologies))
        : new RelationshipTarget(match.toValueMatch());
  }

  /**
   * Builds a relationship type. In JSON a type is just its match, so it has no record of its own.
   *
   * @param nameOrCodeMatch how the type name or code must match
   * @return the relationship type
   */
  static RelationshipType toRelationshipType(final ValueMatchDto nameOrCodeMatch) {
    return new RelationshipType(nameOrCodeMatch.toValueMatch());
  }
}
