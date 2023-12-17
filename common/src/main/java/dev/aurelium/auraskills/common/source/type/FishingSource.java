package dev.aurelium.auraskills.common.source.type;

import dev.aurelium.auraskills.api.item.LootItemFilter;
import dev.aurelium.auraskills.api.registry.NamespacedId;
import dev.aurelium.auraskills.api.source.type.FishingXpSource;
import dev.aurelium.auraskills.common.AuraSkillsPlugin;
import dev.aurelium.auraskills.common.source.Source;
import org.jetbrains.annotations.NotNull;

public class FishingSource extends Source implements FishingXpSource {

    private final LootItemFilter item;

    public FishingSource(AuraSkillsPlugin plugin, NamespacedId id, double xp, String displayName, LootItemFilter item) {
        super(plugin, id, xp, displayName);
        this.item = item;
    }

    @Override
    public @NotNull LootItemFilter getItem() {
        return item;
    }
}
