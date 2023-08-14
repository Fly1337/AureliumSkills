package dev.aurelium.auraskills.common.source;

import dev.aurelium.auraskills.api.item.ItemFilter;
import dev.aurelium.auraskills.api.item.ItemFilterMeta;
import dev.aurelium.auraskills.api.item.LootItemFilter;
import dev.aurelium.auraskills.api.item.PotionData;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.skill.Skills;
import dev.aurelium.auraskills.api.source.XpSource;
import dev.aurelium.auraskills.api.source.type.BlockXpSource;
import dev.aurelium.auraskills.common.AuraSkillsPlugin;
import dev.aurelium.auraskills.common.config.ConfigurateLoader;
import dev.aurelium.auraskills.common.source.serializer.BlockSourceSerializer;
import dev.aurelium.auraskills.common.source.serializer.SourceSerializer;
import dev.aurelium.auraskills.common.source.serializer.util.ItemFilterMetaSerializer;
import dev.aurelium.auraskills.common.source.serializer.util.ItemFilterSerializer;
import dev.aurelium.auraskills.common.source.serializer.util.LootItemFilterSerializer;
import dev.aurelium.auraskills.common.source.serializer.util.PotionDataSerializer;
import dev.aurelium.auraskills.common.util.text.TextUtil;
import org.spongepowered.configurate.ConfigurationNode;
import org.spongepowered.configurate.serialize.SerializationException;
import org.spongepowered.configurate.serialize.TypeSerializerCollection;

import java.lang.reflect.InvocationTargetException;
import java.util.*;

public class SourceLoader {

    private final AuraSkillsPlugin plugin;
    private final ConfigurateLoader configurateLoader;

    public SourceLoader(AuraSkillsPlugin plugin) {
        this.plugin = plugin;
        // Register utility serializers
        TypeSerializerCollection sourceSerializers = TypeSerializerCollection.builder()
                .register(ItemFilterMeta.class, new ItemFilterMetaSerializer(plugin, ""))
                .register(ItemFilter.class, new ItemFilterSerializer(plugin, ""))
                .register(LootItemFilter.class, new LootItemFilterSerializer(plugin, ""))
                .register(PotionData.class, new PotionDataSerializer(plugin, ""))
                .register(BlockXpSource.BlockXpSourceState.class, new BlockSourceSerializer.BlockSourceStateSerializer(plugin, ""))
                .build();
        this.configurateLoader = new ConfigurateLoader(plugin, sourceSerializers);
    }

    public List<XpSource> loadSources(Skills skill) {
        String fileName = "sources/" + skill.name().toLowerCase(Locale.ROOT) + ".yml";
        try {
            ConfigurationNode embedded = configurateLoader.loadEmbeddedFile(fileName);

            // Get the node containing default values in the file
            ConfigurationNode fileDefault = embedded.node("default");

            // Load each embedded source
            Map<String, ConfigurationNode> embeddedSources = new HashMap<>();
            addToMap(embedded, embeddedSources);

            // Load each user source file
            ConfigurationNode user = configurateLoader.loadUserFile(fileName);

            ConfigurationNode userDefault = user.node("default");

            Map<String, ConfigurationNode> userSources = new HashMap<>();
            addToMap(user, userSources);

            // Merge embedded and user sources
            Map<String, ConfigurationNode> sources = new HashMap<>();

            for (String sourceName : userSources.keySet()) {
                ConfigurationNode embeddedNode = embeddedSources.get(sourceName);
                ConfigurationNode userNode = userSources.get(sourceName);

                ConfigurationNode merged = configurateLoader.mergeNodes(fileDefault, embeddedNode, userDefault, userNode);
                sources.put(sourceName, merged);
            }

            // Deserialize each source
            List<XpSource> deserializedSources = new ArrayList<>();
            for (Map.Entry<String, ConfigurationNode> entry : sources.entrySet()) {
                String sourceName = entry.getKey();
                ConfigurationNode sourceNode = entry.getValue();

                NamespacedId id = NamespacedId.from(NamespacedId.AURASKILLS, sourceName);

                String type = sourceNode.node("type").getString();
                if (type == null) {
                    throw new IllegalArgumentException("Source " + id + " must specify a type");
                }
                applyNodeReplacements(sourceNode, sourceNode, sourceName);
                Source source = parseSourceFromType(type, sourceNode, sourceName);
                deserializedSources.add(source);
                registerMenuItem(source, sourceNode);
            }
            return deserializedSources;
        } catch (Exception e) {
            plugin.logger().warn("Error loading source file " + fileName + ": " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    public Map<SourceTag, List<XpSource>> loadTags(Skills skill) {
        String fileName = "sources/" + skill.name().toLowerCase(Locale.ROOT) + ".yml";
        try {
            Map<SourceTag, List<XpSource>> tagMap = new HashMap<>();

            ConfigurationNode user = configurateLoader.loadUserFile(fileName);

            for (SourceTag tag : SourceTag.ofSkill(skill)) {
                ConfigurationNode tagNode = user.node("tags").node(tag.name().toLowerCase(Locale.ROOT));

                // Parse sources from string list
                List<XpSource> sourceList = new ArrayList<>();
                for (String sourceString : tagNode.getList(String.class, new ArrayList<>())) {
                    if (sourceString.equals("*")) { // Add all sources in skill
                        sourceList.addAll(skill.getSources());
                    } else if (sourceString.startsWith("!")) { // Remove source if starts with !
                        NamespacedId id = NamespacedId.fromDefault(sourceString.substring(1));
                        XpSource source = plugin.getSkillManager().getSourceById(id);
                        if (source != null) {
                            sourceList.remove(source);
                        }
                    } else { // Add raw source name
                        XpSource source = plugin.getSkillManager().getSourceById(NamespacedId.fromDefault(sourceString));
                        if (source != null) {
                            sourceList.add(source);
                        }
                    }
                }

                tagMap.put(tag, sourceList);
            }

            return tagMap;
        } catch (Exception e) {
            plugin.logger().warn("Error loading tags in sources file " + fileName + ": " + e.getMessage());
            e.printStackTrace();
            // Return map with empty lists for each tag
            Map<SourceTag, List<XpSource>> fallback = new HashMap<>();
            for (SourceTag tag : SourceTag.ofSkill(skill)) {
                fallback.put(tag, new ArrayList<>());
            }
            return fallback;
        }
    }

    private void addToMap(ConfigurationNode root, Map<String, ConfigurationNode> sourcesMap) {
        root.node("sources").childrenMap().forEach((key, sourceNode) -> {
            String sourceName = key.toString();
            if (!sourceNode.isMap()) { // Replace leaf node to a child node with key _value
                try {
                    sourceNode.node("_value").set(sourceNode.raw());
                } catch (SerializationException e) {
                    throw new RuntimeException(e);
                }
            }
            sourcesMap.put(sourceName, sourceNode);
        });
    }

    private Source parseSourceFromType(String type, ConfigurationNode sourceNode, String sourceName) {
        SourceType sourceType = SourceType.valueOf(type.toUpperCase(Locale.ROOT));

        Class<?> serializerClass = sourceType.getSerializerClass();
        // Create new instance of serializer
        try {
            SourceSerializer<?> sourceSerializer = (SourceSerializer<?>) serializerClass.getConstructors()[0].newInstance(plugin, sourceName);

            return (Source) sourceSerializer.deserialize(sourceType.getSourceClass(), sourceNode);
        } catch (IllegalAccessException | InvocationTargetException | InstantiationException e) {
            throw new RuntimeException("Error creating serializer for source of type " + type);
        } catch (SerializationException e) {
            throw new IllegalArgumentException("Error deserializing source of type " + type);
        }
    }

    private void registerMenuItem(Source source, ConfigurationNode sourceNode) throws SerializationException {
        // Parse menu item if present
        ConfigurationNode menuNode = sourceNode.node("menu_item");
        if (!menuNode.virtual()) {
            plugin.getItemRegistry().getSourceMenuItems().parseAndRegisterMenuItem(source, menuNode);
        }
        // Parse unit name if present
        ConfigurationNode unitNode = sourceNode.node("unit");
        if (!unitNode.virtual()) {
            plugin.getItemRegistry().getSourceMenuItems().registerSourceUnit(source, unitNode.getString());
        }
    }

    /**
     * Searches baseNode for any String values with placeholders marked between curly braces
     * and replaces them with the corresponding value from parentNode where the key of the parentNode is the placeholder value
     *
     * @param baseNode The baseNode to search for placeholders and modify
     * @param parentNode The parentNode to get the replacement values from
     */
    private void applyNodeReplacements(ConfigurationNode baseNode, ConfigurationNode parentNode, String sourceName) throws SerializationException {
        for (ConfigurationNode child : baseNode.childrenMap().values()) {
            String text = child.getString();
            if (text != null) {
                // Get all placeholders between curly braces in text
                List<String> placeholders = TextUtil.getPlaceholders(text);
                for (String placeholder : placeholders) {
                    if (placeholder.equals("key")) { // Replace key with source name
                        child.set(text.replace("{key}", sourceName));
                        continue;
                    }
                    if (placeholder.equals("value")) {
                        child.set(text.replace("{value}", String.valueOf(parentNode.node("_value").raw())));
                    }
                    // Get replacement value from parentNode
                    String[] path = placeholder.split("\\."); // Split placeholder into path by periods
                    String replacement = parentNode.node((Object[]) path).getString();
                    if (replacement != null) {
                        // Replace placeholder with replacement value
                        child.set(text.replace("{" + placeholder + "}", replacement));
                    }
                }
            } else {
                applyNodeReplacements(child, parentNode, sourceName); // Recursively search for placeholders
            }
        }
    }
}
