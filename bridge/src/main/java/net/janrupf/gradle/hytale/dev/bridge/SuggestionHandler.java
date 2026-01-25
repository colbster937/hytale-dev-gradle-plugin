package net.janrupf.gradle.hytale.dev.bridge;

import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.command.system.arguments.system.RequiredArg;
import com.hypixel.hytale.server.core.console.ConsoleSender;
import net.janrupf.gradle.hytale.dev.protocol.HytaleBridgeProto.GetSuggestionsRequest;
import net.janrupf.gradle.hytale.dev.protocol.HytaleBridgeProto.SuggestionsResponse;

import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/**
 * Handles dynamic command suggestions for IDE autocomplete.
 * <p>
 * Provides real-time suggestions for command names and arguments
 * based on the current input text and cursor position.
 */
public class SuggestionHandler {
    private static final SuggestionHandler INSTANCE = new SuggestionHandler();
    private static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();
    private static final int MAX_SUGGESTIONS = 20;

    public static SuggestionHandler getInstance() {
        return INSTANCE;
    }

    private SuggestionHandler() {
    }

    /**
     * Get suggestions for a partial command.
     *
     * @param request the suggestion request containing partial command and cursor position
     * @return suggestions response with matching completions
     */
    public SuggestionsResponse getSuggestions(GetSuggestionsRequest request) {
        String partial = request.getPartialCommand();
        int cursorPos = Math.min(request.getCursorPosition(), partial.length());

        String textBeforeCursor = partial.substring(0, cursorPos);
        String[] tokens = textBeforeCursor.split("\\s+");

        SuggestionsResponse.Builder builder = SuggestionsResponse.newBuilder();
        CommandManager manager = CommandManager.get();
        if (manager == null) {
            LOGGER.at(Level.FINE).log("CommandManager not available for suggestions");
            return builder.build();
        }

        if (tokens.length == 0 || (tokens.length == 1 && !textBeforeCursor.contains(" "))) {
            // Completing command name
            String prefix = tokens.length > 0 ? tokens[0].toLowerCase() : "";
            completeCommandName(manager, prefix, builder);
            builder.setStartPosition(0);
        } else {
            // Completing argument - navigate to command and get arg suggestions
            AbstractCommand cmd = navigateToCommand(manager, tokens);
            if (cmd != null) {
                int argIndex = calculateArgumentIndex(tokens, cmd);
                completeArgument(cmd, argIndex, tokens, builder);
            }
            builder.setStartPosition(textBeforeCursor.lastIndexOf(' ') + 1);
        }

        return builder.build();
    }

    /**
     * Complete command names matching the given prefix.
     */
    private void completeCommandName(CommandManager manager, String prefix, SuggestionsResponse.Builder builder) {
        for (Map.Entry<String, AbstractCommand> entry : manager.getCommandRegistration().entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                builder.addSuggestions(entry.getKey());
                if (builder.getSuggestionsCount() >= MAX_SUGGESTIONS) return;
            }
            // Also check aliases
            for (String alias : entry.getValue().getAliases()) {
                if (alias.startsWith(prefix) && !alias.equals(entry.getKey())) {
                    builder.addSuggestions(alias);
                    if (builder.getSuggestionsCount() >= MAX_SUGGESTIONS) return;
                }
            }
        }
    }

    /**
     * Complete an argument at the given index.
     */
    private void completeArgument(AbstractCommand cmd, int argIndex, String[] tokens, SuggestionsResponse.Builder builder) {
        List<RequiredArg<?>> reqArgs = cmd.getRequiredArguments();

        if (argIndex >= 0 && argIndex < reqArgs.size()) {
            RequiredArg<?> arg = reqArgs.get(argIndex);
            String partialArg = tokens.length > 0 ? tokens[tokens.length - 1] : "";

            try {
                // Use Hytale's suggestion system with ConsoleSender
                List<String> suggestions = arg.getSuggestions(
                        ConsoleSender.INSTANCE,
                        new String[]{partialArg}
                );
                for (String s : suggestions) {
                    builder.addSuggestions(s);
                    if (builder.getSuggestionsCount() >= MAX_SUGGESTIONS) break;
                }
            } catch (Exception e) {
                LOGGER.at(Level.FINE).withCause(e).log("Failed to get suggestions for argument %s", arg.getName());
            }
        } else if (argIndex < 0) {
            // Still completing subcommand name
            String partialSub = tokens.length > 0 ? tokens[tokens.length - 1].toLowerCase() : "";
            for (AbstractCommand sub : cmd.getSubCommands().values()) {
                String subName = sub.getName();
                if (subName != null && subName.startsWith(partialSub)) {
                    builder.addSuggestions(subName);
                    if (builder.getSuggestionsCount() >= MAX_SUGGESTIONS) return;
                }
                for (String alias : sub.getAliases()) {
                    if (alias.startsWith(partialSub)) {
                        builder.addSuggestions(alias);
                        if (builder.getSuggestionsCount() >= MAX_SUGGESTIONS) return;
                    }
                }
            }
        }
    }

    /**
     * Navigate through the command tree following the token path.
     */
    private AbstractCommand navigateToCommand(CommandManager manager, String[] tokens) {
        if (tokens.length == 0) return null;

        AbstractCommand current = manager.getCommandRegistration().get(tokens[0].toLowerCase());
        if (current == null) {
            // Check aliases
            for (AbstractCommand cmd : manager.getCommandRegistration().values()) {
                if (cmd.getAliases().contains(tokens[0].toLowerCase())) {
                    current = cmd;
                    break;
                }
            }
        }
        if (current == null) return null;

        // Navigate through subcommands
        for (int i = 1; i < tokens.length - 1 && current != null; i++) {
            String token = tokens[i].toLowerCase();
            AbstractCommand sub = current.getSubCommands().get(token);
            if (sub != null) {
                current = sub;
            } else {
                // Check subcommand aliases
                boolean found = false;
                for (AbstractCommand subCmd : current.getSubCommands().values()) {
                    if (subCmd.getAliases().contains(token)) {
                        current = subCmd;
                        found = true;
                        break;
                    }
                }
                if (!found) {
                    // Token is an argument, stop navigating
                    break;
                }
            }
        }

        return current;
    }

    /**
     * Calculate which argument index we're currently completing.
     *
     * @return argument index (0-based) or -1 if still completing subcommand
     */
    private int calculateArgumentIndex(String[] tokens, AbstractCommand cmd) {
        // Count how many tokens are consumed by command/subcommand path
        int consumed = 1; // The root command
        AbstractCommand current = cmd;

        for (int i = 1; i < tokens.length - 1; i++) {
            String token = tokens[i].toLowerCase();
            AbstractCommand sub = current.getSubCommands().get(token);
            if (sub == null) {
                // Check aliases
                for (AbstractCommand subCmd : current.getSubCommands().values()) {
                    if (subCmd.getAliases().contains(token)) {
                        sub = subCmd;
                        break;
                    }
                }
            }
            if (sub != null) {
                current = sub;
                consumed++;
            } else {
                // Token is an argument, stop counting
                break;
            }
        }

        // Remaining tokens after command/subcommand path are arguments
        return tokens.length - consumed - 1;
    }
}
