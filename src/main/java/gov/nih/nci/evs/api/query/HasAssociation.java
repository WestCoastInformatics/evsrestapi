package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;

/**
 * Matches concepts that have an association of the given type, optionally pointing to the given
 * target, e.g. concepts in a subset of the CTRP Terminology. A {@link HasRelationship} on the
 * concept's {@code associations}: see it for how matching works.
 */
public final class HasAssociation extends HasRelationship {

  /** The concept document's list of associations, mapped as nested so they can be searched. */
  private static final String ASSOCIATIONS_FIELD_NAME = "associations";

  /**
   * Instantiates a {@link HasAssociation} that matches an association of the given type, pointing
   * to any concept.
   *
   * @param terminologies the terminologies being searched
   * @param associationType the association's type
   */
  public HasAssociation(
      final List<Terminology> terminologies, final RelationshipType associationType) {
    super(terminologies, associationType);
  }

  /**
   * Instantiates a {@link HasAssociation} that matches an association of the given type pointing to
   * the target.
   *
   * @param terminologies the terminologies being searched
   * @param associationType the association's type
   * @param associationTarget the concepts the association must point to
   */
  public HasAssociation(
      final List<Terminology> terminologies,
      final RelationshipType associationType,
      final RelationshipTarget associationTarget) {
    super(terminologies, associationType, associationTarget);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ASSOCIATIONS_FIELD_NAME;
  }
}
