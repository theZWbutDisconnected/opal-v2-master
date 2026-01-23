package wtf.opal.client.command.impl.config;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import wtf.opal.client.command.Command;
import wtf.opal.client.command.arguments.ConfigArgumentType;
import wtf.opal.utility.data.SaveUtility;
import wtf.opal.utility.misc.chat.ChatUtility;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;

import static com.mojang.brigadier.Command.SINGLE_SUCCESS;
import static wtf.opal.client.Constants.DIRECTORY;

public final class ConfigCommand extends Command {

    public ConfigCommand() {
        super("config", "Interacts with configs.", "c");
    }

    @Override
    protected void onCommand(final LiteralArgumentBuilder<CommandSource> builder) {
        builder.then(literal("save").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = context.getArgument("config_name", String.class).toLowerCase();

            SaveUtility.saveConfig(configName);
            SaveUtility.saveBindings();
            
            ChatUtility.success("Config '" + configName + "' saved successfully!");

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("list").executes(context -> {
            if (!DIRECTORY.exists()) {
                ChatUtility.print("No configs found.");
                return SINGLE_SUCCESS;
            }

            File[] configFiles = DIRECTORY.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("bindings.json"));
            
            if (configFiles == null || configFiles.length == 0) {
                ChatUtility.print("No configs found.");
                return SINGLE_SUCCESS;
            }

            Arrays.sort(configFiles, Comparator.comparing(File::getName));
            
            ChatUtility.print("Available configs (" + configFiles.length + "):");
            for (File configFile : configFiles) {
                String configName = configFile.getName().replace(".json", "");
                ChatUtility.print("- " + configName);
            }

            return SINGLE_SUCCESS;
        }));

        builder.then(literal("load").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = context.getArgument("config_name", String.class).toLowerCase();

            boolean success = SaveUtility.loadConfigFromFile(configName);
            if (success) {
                ChatUtility.success("Config '" + configName + "' loaded successfully!");
            } else {
                ChatUtility.error("Failed to load config '" + configName + "'");
            }

            return SINGLE_SUCCESS;
        })));

        builder.then(literal("delete").then(argument("config_name", ConfigArgumentType.create()).executes(context -> {
            final String configName = context.getArgument("config_name", String.class).toLowerCase();
            final File configFile = new File(DIRECTORY, configName + ".json");

            if (!configFile.exists()) {
                ChatUtility.error("Config '" + configName + "' does not exist.");
                return SINGLE_SUCCESS;
            }

            if (configFile.delete()) {
                ChatUtility.success("Config '" + configName + "' deleted successfully!");
            } else {
                ChatUtility.error("Failed to delete config '" + configName + "'");
            }

            return SINGLE_SUCCESS;
        })));
    }
}