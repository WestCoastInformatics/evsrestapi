package gov.nih.nci.evs.api.query;

import gov.nih.nci.evs.api.model.Terminology;
import java.util.List;

/**
 * Matches concepts that another concept's association of the given type points to, optionally from
 * the given source: {@link HasAssociation} in the other direction. E.g. the subsets Cutaneous
 * Melanoma is in, which are the concepts its Concept_In_Subset associations point to.
 *
 * <p>A {@link HasRelationship} on the concept's {@code inverseAssociations}: see it for how
 * matching works. Each entry there mirrors another concept's association, with the same type, and
 * with that other concept as the entry's related concept. So the {@link RelationshipTarget} here is
 * the association's source: the concept holding the association, not the one it points to.
 */
public final class HasInverseAssociation extends HasRelationship {

  /**
   * The concept document's list of inverse associations, mapped as nested so they can be searched.
   */
  private static final String INVERSE_ASSOCIATIONS_FIELD_NAME = "inverseAssociations";

  /**
   * Instantiates a {@link HasInverseAssociation} that matches concepts an association of the given
   * type points to, from any concept.
   *
   * @param terminologies the terminologies being searched
   * @param associationType the association's type
   */
  public HasInverseAssociation(
      final List<Terminology> terminologies, final RelationshipType associationType) {
    super(terminologies, associationType);
  }

  /**
   * Instantiates a {@link HasInverseAssociation} that matches concepts an association of the given
   * type points to, from the given source.
   *
   * @param terminologies the terminologies being searched
   * @param associationType the association's type
   * @param associationSource the concepts the association must come from
   */
  public HasInverseAssociation(
      final List<Terminology> terminologies,
      final RelationshipType associationType,
      final RelationshipTarget associationSource) {
    super(terminologies, associationType, associationSource);
  }

  /* see superclass */
  @Override
  protected String getRelationshipListFieldName() {
    return INVERSE_ASSOCIATIONS_FIELD_NAME;
  }
}
