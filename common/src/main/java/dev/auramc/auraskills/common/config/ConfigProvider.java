package dev.auramc.auraskills.common.config;

import dev.auramc.auraskills.api.skill.Skill;

public interface ConfigProvider {

    boolean getBoolean(Option option);

    int getInt(Option option);

    double getDouble(Option option);

    String getString(Option option);

    String[] getStringList(Option option);

    String getColor(Option option);

    boolean isEnabled(Skill skill);

    int getMaxLevel(Skill skill);

    int getHighestMaxLevel();

}