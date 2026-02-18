package wtf.opal.client.feature.module.impl.world.scaffold.prediction;

import net.minecraft.client.MinecraftClient;
import wtf.opal.client.feature.module.Module;
import wtf.opal.client.feature.module.ModuleCategory;
import wtf.opal.client.feature.module.impl.world.scaffold.ScaffoldSettings;

public class ScaffoldPrediction extends Module {
    private static final MinecraftClient mc = MinecraftClient.getInstance();

    private final ScaffoldPredictionSettings settings = new ScaffoldPredictionSettings(this);

    public ScaffoldPrediction() {
        super("ScaffoldPrediction", "Port from OpenMYAU.", ModuleCategory.WORLD);
    }

    public ScaffoldPredictionSettings getSettings() {
        return settings;
    }
}
