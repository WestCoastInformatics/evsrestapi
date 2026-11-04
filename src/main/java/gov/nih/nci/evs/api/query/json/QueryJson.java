package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.InjectableValues;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import gov.nih.nci.evs.api.model.Terminology;
import gov.nih.nci.evs.api.query.AllQualifiers;
import gov.nih.nci.evs.api.query.AncestorOf;
import gov.nih.nci.evs.api.query.AncestorOrSelfOf;
import gov.nih.nci.evs.api.query.And;
import gov.nih.nci.evs.api.query.AnyQualifier;
import gov.nih.nci.evs.api.query.Boost;
import gov.nih.nci.evs.api.query.DescendantOf;
import gov.nih.nci.evs.api.query.DescendantOrSelfOf;
import gov.nih.nci.evs.api.query.EvsQuery;
import gov.nih.nci.evs.api.query.HasAssociation;
import gov.nih.nci.evs.api.query.HasInverseAssociation;
import gov.nih.nci.evs.api.query.HasInverseRole;
import gov.nih.nci.evs.api.query.HasProperty;
import gov.nih.nci.evs.api.query.HasRelationship;
import gov.nih.nci.evs.api.query.HasRole;
import gov.nih.nci.evs.api.query.HasSynonym;
import gov.nih.nci.evs.api.query.HierarchyQuery;
import gov.nih.nci.evs.api.query.InSubset;
import gov.nih.nci.evs.api.query.MatchAnyOf;
import gov.nih.nci.evs.api.query.NoQualifier;
import gov.nih.nci.evs.api.query.Not;
import gov.nih.nci.evs.api.query.OneOf;
import gov.nih.nci.evs.api.query.Or;
import gov.nih.nci.evs.api.query.PropertyQualifier;
import gov.nih.nci.evs.api.query.QualifierCondition;
import gov.nih.nci.evs.api.query.RelationshipTarget;
import gov.nih.nci.evs.api.query.RelationshipType;
import gov.nih.nci.evs.api.query.Typeahead;
import gov.nih.nci.evs.api.query.ValueMatch;
import gov.nih.nci.evs.api.query.json.QueryMixins.AllQualifiersMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.AncestorOfMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.AncestorOrSelfOfMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.AndMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.AnyQualifierMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.BoostMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.DescendantOfMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.DescendantOrSelfOfMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.EvsQueryMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasAssociationMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasInverseAssociationMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasInverseRoleMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasPropertyMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasRelationshipMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasRoleMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.HasSynonymMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.InSubsetMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.MatchAnyOfMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.NoQualifierMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.NotMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.OneOfMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.OrMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.PropertyQualifierMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.QualifierConditionMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.RelationshipTargetMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.RelationshipTypeMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.TypeaheadMixin;
import gov.nih.nci.evs.api.query.json.QueryMixins.ValueMatchMixin;
import java.io.IOException;
import java.util.List;
import org.opensearch.common.unit.Fuzziness;

/**
 * Converts query trees to and from JSON, the request format of the composable search endpoint.
 *
 * <p>This branch keeps JSON out of the query nodes with Jackson mix-ins: the annotations live in
 * {@link QueryMixins} and are attached to the query classes here, on the mapper. Jackson reads and
 * writes the nodes themselves. Each JSON object names its node with a lower-camel-case {@code
 * type}; nodes nest, so trees do too:
 *
 * <pre>{@code
 * {"type":"and","children":[
 *   {"type":"descendantOf","code":"C3262","maxLevel":3},
 *   {"type":"hasRole","relationshipType":{"type":"exact","term":"R101"},
 *    "target":{"query":{"type":"descendantOrSelfOf","code":"C12470"}}}]}
 * }</pre>
 *
 * <p>The JSON never names terminologies: the request does (one terminology per request, from the
 * path), and {@link #readQuery} injects them into every node it builds. Unknown types and
 * properties fail rather than being dropped, and so does a {@code terminologies} property.
 */
public final class QueryJson {

  /** The property naming each JSON object's node, match, or condition type. */
  static final String TYPE_PROPERTY = "type";

  /** Name under which the request's terminologies are injected into each node. */
  static final String TERMINOLOGIES = "terminologies";

  /** The mapper, with the mix-ins attached. Thread safe once built; each read gets a reader. */
  private static final ObjectMapper MAPPER =
      JsonMapper.builder()
          .enable(
              DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
              DeserializationFeature.FAIL_ON_IGNORED_PROPERTIES)
          .defaultPropertyInclusion(JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, null))
          .addMixIn(EvsQuery.class, EvsQueryMixin.class)
          .addMixIn(And.class, AndMixin.class)
          .addMixIn(Or.class, OrMixin.class)
          .addMixIn(Not.class, NotMixin.class)
          .addMixIn(Boost.class, BoostMixin.class)
          .addMixIn(DescendantOf.class, DescendantOfMixin.class)
          .addMixIn(DescendantOrSelfOf.class, DescendantOrSelfOfMixin.class)
          .addMixIn(AncestorOf.class, AncestorOfMixin.class)
          .addMixIn(AncestorOrSelfOf.class, AncestorOrSelfOfMixin.class)
          .addMixIn(HasRelationship.class, HasRelationshipMixin.class)
          .addMixIn(HasRole.class, HasRoleMixin.class)
          .addMixIn(HasInverseRole.class, HasInverseRoleMixin.class)
          .addMixIn(HasAssociation.class, HasAssociationMixin.class)
          .addMixIn(HasInverseAssociation.class, HasInverseAssociationMixin.class)
          .addMixIn(RelationshipType.class, RelationshipTypeMixin.class)
          .addMixIn(RelationshipTarget.class, RelationshipTargetMixin.class)
          .addMixIn(HasProperty.class, HasPropertyMixin.class)
          .addMixIn(HasSynonym.class, HasSynonymMixin.class)
          .addMixIn(InSubset.class, InSubsetMixin.class)
          .addMixIn(MatchAnyOf.class, MatchAnyOfMixin.class)
          .addMixIn(Typeahead.class, TypeaheadMixin.class)
          .addMixIn(ValueMatch.class, ValueMatchMixin.class)
          .addMixIn(OneOf.class, OneOfMixin.class)
          .addMixIn(QualifierCondition.class, QualifierConditionMixin.class)
          .addMixIn(PropertyQualifier.class, PropertyQualifierMixin.class)
          .addMixIn(AllQualifiers.class, AllQualifiersMixin.class)
          .addMixIn(AnyQualifier.class, AnyQualifierMixin.class)
          .addMixIn(NoQualifier.class, NoQualifierMixin.class)
          .addModule(
              new SimpleModule()
                  .addSerializer(HierarchyQuery.class, new HierarchyQuerySerializer())
                  .addSerializer(Fuzziness.class, new FuzzinessSerializer())
                  .addDeserializer(Fuzziness.class, new FuzzinessDeserializer()))
          .build();

  /** Only static helpers. */
  private QueryJson() {}

  /**
   * Builds a query tree from its JSON form.
   *
   * @param json the JSON
   * @param terminologies the terminologies being searched, given to every node
   * @return the query tree
   * @throws IllegalArgumentException if the JSON isn't a valid query, with the reason
   */
  public static EvsQuery readQuery(final String json, final List<Terminology> terminologies) {
    final InjectableValues requestTerminologies =
        new InjectableValues.Std().addValue(TERMINOLOGIES, List.copyOf(terminologies));
    try {
      return MAPPER.readerFor(EvsQuery.class).with(requestTerminologies).readValue(json);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Invalid query JSON: " + getReason(e), e);
    }
  }

  /**
   * Writes a query tree as JSON.
   *
   * @param query the query tree
   * @return the JSON
   * @throws IllegalArgumentException if the tree can't be written
   */
  public static String writeQuery(final EvsQuery query) {
    try {
      return MAPPER.writeValueAsString(query);
    } catch (final JsonProcessingException e) {
      throw new IllegalArgumentException("Query can't be written as JSON: " + getReason(e), e);
    }
  }

  /**
   * Returns why a JSON read or write failed. A node's own check (e.g. a max level below 1) is the
   * cause of Jackson's error, and is clearer than Jackson's wrapper message.
   *
   * @param e the error
   * @return the reason
   */
  private static String getReason(final JsonProcessingException e) {
    final boolean isNodeCheck = e.getCause() instanceof IllegalArgumentException;
    return isNodeCheck ? e.getCause().getMessage() : e.getOriginalMessage();
  }

  /** Writes a fuzziness as its text, e.g. "AUTO" or "1". */
  private static final class FuzzinessSerializer extends StdSerializer<Fuzziness> {

    /** Instantiates a {@link FuzzinessSerializer}. */
    FuzzinessSerializer() {
      super(Fuzziness.class);
    }

    /* see superclass */
    @Override
    public void serialize(
        final Fuzziness fuzziness, final JsonGenerator generator, final SerializerProvider provider)
        throws IOException {
      generator.writeString(fuzziness.asString());
    }
  }

  /** Reads a fuzziness from its text. A missing fuzziness is AUTO, as for {@code new Fuzzy(t)}. */
  private static final class FuzzinessDeserializer extends StdDeserializer<Fuzziness> {

    /** Instantiates a {@link FuzzinessDeserializer}. */
    FuzzinessDeserializer() {
      super(Fuzziness.class);
    }

    /* see superclass */
    @Override
    public Fuzziness deserialize(final JsonParser parser, final DeserializationContext context)
        throws IOException {
      return Fuzziness.build(parser.getValueAsString());
    }

    /* see superclass */
    @Override
    public Object getAbsentValue(final DeserializationContext context) {
      return Fuzziness.AUTO;
    }
  }
}
