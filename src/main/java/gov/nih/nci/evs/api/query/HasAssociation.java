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
   * @param associationTypeNameOrCodeMatch how the association's type name or code must match
   */
  public HasAssociation(final ValueMatch associationTypeNameOrCodeMatch) {
    super(associationTypeNameOrCodeMatch);
  }

  /**
   * Instantiates a {@link HasAssociation} that matches an association of the given type pointing to
   * the target.
   *
   * @param associationTypeNameOrCodeMatch how the association's type name or code must match
   * @param associationTargetNameOrCodeMatch how the name or code of the concept the association
   *     must point to
   */
  public HasAssociation(
      final ValueMatch associationTypeNameOrCodeMatch,
      final ValueMatch associationTargetNameOrCodeMatch) {
    super(associationTypeNameOrCodeMatch, associationTargetNameOrCodeMatch);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ASSOCIATIONS_FIELD_NAME;
  }
}
