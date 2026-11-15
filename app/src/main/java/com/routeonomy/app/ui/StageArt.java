package com.routeonomy.app.ui;

import com.routeonomy.app.R;
import com.routeonomy.app.domain.GrowthStage;

/** Character pictures per growth stage (cut from the team's character sheet, design/character_sheet.png). */
final class StageArt {
    private StageArt() {}

    /** Standing character of [stage]. */
    static int character(GrowthStage stage) {
        switch (stage) {
            case SPROUT: return R.drawable.char_sprout;
            case STEM: return R.drawable.char_stem;
            case FLOWER: return R.drawable.char_flower;
            case FRUIT: return R.drawable.char_fruit;
            default: return R.drawable.char_seed;
        }
    }

    /** Celebration picture for reaching [stage] (the seed has none: it is the start). */
    static int levelUp(GrowthStage stage) {
        switch (stage) {
            case SPROUT: return R.drawable.levelup_sprout;
            case STEM: return R.drawable.levelup_stem;
            case FLOWER: return R.drawable.levelup_flower;
            case FRUIT: return R.drawable.levelup_fruit;
            default: return character(stage);
        }
    }
}
