package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import org.bukkit.Bukkit;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Shared pieces of the /spider command tree. */
final class CommandSupport {
    private CommandSupport() {
    }

    /** The world border's furthest reach; x and z arguments stay inside it. */
    static final double COORDINATE_LIMIT = 29999984.0;

    /** A command failure shown to the sender. */
    static CommandSyntaxException error(String message) {
        return new SimpleCommandExceptionType(() -> message).create();
    }

    /** Suggests the options that start with what has been typed, ignoring case. */
    static CompletableFuture<Suggestions> suggest(SuggestionsBuilder builder, Iterable<String> options) {
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        for (String option : options) if (option.toLowerCase(Locale.ROOT).startsWith(remaining)) builder.suggest(option);
        return builder.buildFuture();
    }

    static CompletableFuture<Suggestions> suggestWorlds(SuggestionsBuilder builder) {
        List<String> names = new ArrayList<>();
        for (World world : Bukkit.getWorlds()) names.add(world.getName());
        return suggest(builder, names);
    }
}
