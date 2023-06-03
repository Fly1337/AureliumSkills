package dev.auramc.auraskills.common.source.serializer;

import dev.auramc.auraskills.common.source.type.JumpingSource;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;

import java.lang.reflect.Type;

public class JumpingSourceSerializer extends SourceSerializer<JumpingSource> {

    @Override
    public JumpingSource deserialize(Type type, ConfigurationNode source) throws SerializationException {
        int interval = source.node("interval").getInt(100);

        return new JumpingSource(getId(source), getXp(source), interval);
    }
}
