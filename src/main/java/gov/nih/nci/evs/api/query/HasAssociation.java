package gov.nih.nci.evs.api.query;

/**
 * Matches concepts that have an association of the given type, optionally pointing to the given
 * target, e.g. concepts in a subset of the CTRP Terminology. A {@link HasRelationship} on the
 * concept's {@code associations}: see it for how matching works.
 */
public final class HasAssociation extends HasRelationship {

  /** The concept document's list of associations, mapped as nested so they can be searched. */
  private static final String ASSOCIATIONS_FIELD_NAME = "associations";

  /** Instantiates an empty {@link HasAssociation}. */
  public HasAssociation() {
    // n/a
  }

  /**
   * Instantiates a {@link HasAssociation} that matches an association of the given type, pointing
   * to any concept.
   *
   * @param association how the association's type name or code must match
   */
  public HasAssociation(final ValueMatch association) {
    super(association);
  }

  /**
   * Instantiates a {@link HasAssociation} that matches an association of the given type pointing to
   * the target.
   *
   * @param association how the association's type name or code must match
   * @param target how the name or code of the concept the association must point to
   */
  public HasAssociation(final ValueMatch association, final ValueMatch target) {
    super(association, target);
  }

  /**
   * Returns how the name or code of the association's type, e.g. Concept_In_Subset (A8) must match.
   *
   * @return the association name or code match
   */
  public ValueMatch getAssociation() {
    return getRelationshipType();
  }

  /**
   * Sets how the name or code of the association's type, e.g. Concept_In_Subset (A8) must match.
   *
   * @param association the association name or code match
   */
  public void setAssociation(final ValueMatch association) {
    setRelationshipType(association);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return ASSOCIATIONS_FIELD_NAME;
  }
}
