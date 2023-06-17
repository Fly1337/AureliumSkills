package dev.auramc.auraskills.common.mana;

import dev.auramc.auraskills.common.config.OptionValue;

import java.util.Map;

public record ManaAbilityConfig(boolean enabled, double baseValue, double valuePerLevel,
                                double baseCooldown, double cooldownPerLevel, double baseManaCost,
                                double manaCostPerLevel, int unlock, int levelUp, int maxLevel,
                                Map<String, OptionValue> options) {

}
