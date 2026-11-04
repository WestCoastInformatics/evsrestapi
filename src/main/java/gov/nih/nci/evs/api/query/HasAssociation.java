package gov.nih.nci.evs.api.query;

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
   * @param associationType the association's type
   */
  public HasAssociation(final RelationshipType associationType) {
    super(associationType);
  }

  /**
   * Instantiates a {@link HasAssociation} that matches an association of the given type pointing to
   * the target.
   *
   * @param associationType the association's type
   * @param associationTarget the concepts the association must point to
   */
  public HasAssociation(
      final RelationshipType associationType, final RelationshipTarget associationTarget) {
    super(associationType, associationTarget);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ASSOCIATIONS_FIELD_NAME;
  }
}
