package gov.nih.nci.evs.api.query.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.core.type.WritableTypeId;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.jsontype.TypeSerializer;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import gov.nih.nci.evs.api.query.HierarchyQuery;
import java.io.IOException;

/**
 * Writes a hierarchy node: its {@code code} when built from one, otherwise its {@code anchor}
 * query, and its {@code maxLevel}. A mix-in can't do this: a node built from a code holds an
 * internal anchor query too, and only one of the two may be written.
 */
final class HierarchyQuerySerializer extends StdSerializer<HierarchyQuery> {

  /** The JSON property holding the anchor code. */
  private static final String CODE_PROPERTY = "code";

  /** The JSON property holding the anchor query. */
  private static final String ANCHOR_PROPERTY = "anchor";

  /** The JSON property holding the max level. */
  private static final String MAX_LEVEL_PROPERTY = "maxLevel";

  /** Instantiates a {@link HierarchyQuerySerializer}. */
  HierarchyQuerySerializer() {
    super(HierarchyQuery.class);
  }

  /* see superclass */
  @Override
  public void serialize(
      final HierarchyQuery node, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    generator.writeStartObject(node);
    writeProperties(node, generator, provider);
    generator.writeEndObject();
  }

  /**
   * Writes the node with its {@code type}, which is how nodes are always written.
   *
   * @param node the node
   * @param generator the generator
   * @param provider the provider
   * @param typeSerializer writes the {@code type}
   * @throws IOException if writing fails
   */
  @Override
  public void serializeWithType(
      final HierarchyQuery node,
      final JsonGenerator generator,
      final SerializerProvider provider,
      final TypeSerializer typeSerializer)
      throws IOException {
    final WritableTypeId typeId =
        typeSerializer.writeTypePrefix(
            generator, typeSerializer.typeId(node, JsonToken.START_OBJECT));
    writeProperties(node, generator, provider);
    typeSerializer.writeTypeSuffix(generator, typeId);
  }

  /**
   * Writes the node's properties.
   *
   * @param node the node
   * @param generator the generator
   * @param provider the provider, for writing an anchor query with its type
   * @throws IOException if writing fails
   */
  private static void writeProperties(
      final HierarchyQuery node, final JsonGenerator generator, final SerializerProvider provider)
      throws IOException {
    final boolean hasAnchorCode = node.getAnchorCode() != null;
    if (hasAnchorCode) {
      generator.writeStringField(CODE_PROPERTY, node.getAnchorCode());
    } else {
      provider.defaultSerializeField(ANCHOR_PROPERTY, node.getAnchorQuery(), generator);
    }

    final boolean hasMaxLevel = node.getMaxLevel() != null;
    if (hasMaxLevel) {
      generator.writeNumberField(MAX_LEVEL_PROPERTY, node.getMaxLevel());
    }
  }
}
