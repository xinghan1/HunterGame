package com.huntergame.skill.skills;

public final class SkillActivationResult {
    private static final SkillActivationResult FAILURE = new SkillActivationResult(false, null);
    private static final SkillActivationResult SUCCESS = new SkillActivationResult(true, null);

    private final boolean activated;
    private final Integer durationOverrideSeconds;

    private SkillActivationResult(boolean activated, Integer durationOverrideSeconds) {
        this.activated = activated;
        this.durationOverrideSeconds = durationOverrideSeconds;
    }

    public static SkillActivationResult success() {
        return SUCCESS;
    }

    public static SkillActivationResult successWithDuration(int durationOverrideSeconds) {
        return new SkillActivationResult(true, Math.max(0, durationOverrideSeconds));
    }

    public static SkillActivationResult failure() {
        return FAILURE;
    }

    public boolean isActivated() {
        return activated;
    }

    public Integer getDurationOverrideSeconds() {
        return durationOverrideSeconds;
    }
}

