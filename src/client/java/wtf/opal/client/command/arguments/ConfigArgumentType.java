package wtf.opal.client.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.command.CommandSource;
import wtf.opal.utility.data.Config;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static wtf.opal.client.Constants.DIRECTORY;

public final class ConfigArgumentType implements ArgumentType<String> {

    private static final ConfigArgumentType INSTANCE = new ConfigArgumentType();

    public static ConfigArgumentType create() {
        return INSTANCE;
    }

    public static String get(final CommandContext<?> context) {
        return context.getArgument("config", String.class);
    }

    private ConfigArgumentType() {
    }

    @Override
    public String parse(final StringReader reader) throws CommandSyntaxException {
        return reader.readString();
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(final CommandContext<S> context, final SuggestionsBuilder builder) {
        List<String> configNames = getConfigNames();
        return CommandSource.suggestMatching(configNames, builder);
    }

    private List<String> getConfigNames() {
        File configDir = DIRECTORY;
        if (!configDir.exists()) {
            return List.of();
        }

        File[] configFiles = configDir.listFiles((dir, name) -> name.endsWith(".json") && !name.equals("bindings.json"));
        
        if (configFiles == null) {
            return List.of();
        }

        return Arrays.stream(configFiles)
                .map(File::getName)
                .map(name -> name.substring(0, name.length() - 5)) // Remove .json extension from file names
                .collect(Collectors.toList());
    }
}