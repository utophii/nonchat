package com.nonxedy.nonchat.util.chat.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.bukkit.entity.Player;

import com.nonxedy.nonchat.config.ModerationConfig;
import com.nonxedy.nonchat.moderation.Violation;
import com.nonxedy.nonchat.moderation.ViolationDetector;
import com.nonxedy.nonchat.moderation.ViolationType;
import com.nonxedy.nonchat.util.chat.MentionCompletionUtil;
import com.nonxedy.nonchat.util.core.colors.ColorUtil;


/**
 * Detects messages containing banned words and regex patterns.
 * Reports a {@link ViolationType#BANNED_WORD} violation; the sanction tiers configured under
 * 'banned-words' and the warning message are applied by the SanctionsManager.
 */
public class WordBlocker implements ViolationDetector {

    private final ModerationConfig config;
    private final Supplier<List<String>> bannedWords;
    private final Supplier<List<String>> bannedPatterns;
    private final BooleanSupplier wholeWords;
    private final Supplier<List<String>> exceptions;
    private volatile CompiledRules compiledRules;

    /**
     * Creates a word blocker that reads its rules from the moderation config.
     * @param config Moderation configuration
     */
    public WordBlocker(ModerationConfig config) {
        this.config = config;
        this.bannedWords = config::getBannedWords;
        this.bannedPatterns = config::getBannedPatterns;
        this.wholeWords = config::isBannedWordsWholeWords;
        this.exceptions = config::getBannedWordsExceptions;
    }

    /**
     * Creates a matcher-only word blocker without action handling (used in unit tests).
     * @param bannedWords List of banned words
     * @param bannedPatterns List of banned regex patterns
     */
    public WordBlocker(List<String> bannedWords, List<String> bannedPatterns) {
        this(bannedWords, bannedPatterns, false, List.of());
    }

    /**
     * Creates a matcher-only word blocker without action handling (used in unit tests).
     * @param bannedWords List of banned words
     * @param bannedPatterns List of banned regex patterns
     * @param wholeWords Whether banned words match only as whole words
     * @param exceptions Words allowed even though they contain a banned word
     */
    public WordBlocker(List<String> bannedWords, List<String> bannedPatterns,
                       boolean wholeWords, List<String> exceptions) {
        this.config = null;
        this.bannedWords = () -> bannedWords;
        this.bannedPatterns = () -> bannedPatterns;
        this.wholeWords = () -> wholeWords;
        this.exceptions = () -> exceptions;
    }

    @Override
    public String bypassPermission() {
        return "nonchat.antiblockedwords";
    }

    /**
     * Checks a message for banned content.
     * @param player Player who sent the message
     * @param message Original message as typed by the player
     * @return a BANNED_WORD violation if the message is blocked, empty if it is allowed
     */
    @Override
    public Optional<Violation> inspect(Player player, String message) {
        if (config != null && !config.isWordBlockingEnabled()) {
            return Optional.empty();
        }

        // Check blocked words on the message without color codes
        if (isMessageAllowed(ColorUtil.stripFormatting(message))) {
            return Optional.empty();
        }

        return Optional.of(new Violation(ViolationType.BANNED_WORD, player.getUniqueId(), message));
    }

    /**
     * Checks if a message is allowed by scanning for banned words and patterns
     * @param message The message to check
     * @return true if message is allowed, false if it contains banned content
     */
    public boolean isMessageAllowed(String message) {
        return isMessageAllowed(message, MentionCompletionUtil::isOnlinePlayerName);
    }

    /**
     * Checks if a message is allowed by scanning for banned words and patterns,
     * ignoring mentions of players matched by {@code onlinePlayerPredicate}.
     *
     * @param message The message to check
     * @param onlinePlayerPredicate Predicate identifying online player names
     * @return true if message is allowed, false if it contains banned content
     */
    public boolean isMessageAllowed(String message, Predicate<String> onlinePlayerPredicate) {
        if (message == null || message.isEmpty()) {
            return true;
        }

        String sanitized = MentionCompletionUtil.stripMentions(
                ColorUtil.stripFormatting(message),
                onlinePlayerPredicate);
        if (sanitized.isEmpty()) {
            return true;
        }

        String lowerMessage = removeExceptions(sanitized.toLowerCase());
        CompiledRules rules = currentRules();

        // Check banned words (case-insensitive)
        if (rules.wholeWords()) {
            for (Pattern word : rules.wordPatterns()) {
                if (word.matcher(lowerMessage).find()) {
                    return false;
                }
            }
        } else {
            for (String word : rules.lowerWords()) {
                if (lowerMessage.contains(word)) {
                    return false;
                }
            }
        }

        // Check regex patterns (case-insensitive)
        for (Pattern regex : rules.patterns()) {
            if (regex.matcher(sanitized).find()) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns compiled rules for the current configuration, recompiling only when it has changed
     * @return Compiled banned words and patterns
     */
    private CompiledRules currentRules() {
        List<String> words = bannedWords.get();
        List<String> patterns = bannedPatterns.get();
        boolean matchWholeWords = wholeWords.getAsBoolean();

        CompiledRules rules = compiledRules;
        if (rules == null || !rules.matches(words, patterns, matchWholeWords)) {
            rules = CompiledRules.compile(words, patterns, matchWholeWords);
            compiledRules = rules;
        }
        return rules;
    }

    /**
     * Removes exception words so banned words inside them are not matched.
     * Text around an exception is still checked, e.g. "classass" with exception "class" is blocked.
     * @param lowerMessage Lowercased message
     * @return Message with exception words replaced by spaces
     */
    private String removeExceptions(String lowerMessage) {
        String result = lowerMessage;
        for (String exception : exceptions.get()) {
            if (!exception.isEmpty()) {
                result = result.replace(exception.toLowerCase(), " ");
            }
        }
        return result;
    }

    // \b only treats ASCII as word characters, so Cyrillic words need explicit Unicode boundaries
    private static Pattern wholeWordPattern(String word) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_])" + Pattern.quote(word) + "(?![\\p{L}\\p{N}_])");
    }

    /**
     * Immutable snapshot of the configured lists together with their compiled forms
     */
    private record CompiledRules(List<String> sourceWords, List<String> sourcePatterns, boolean wholeWords,
                                 List<String> lowerWords, List<Pattern> wordPatterns, List<Pattern> patterns) {

        static CompiledRules compile(List<String> words, List<String> patterns, boolean wholeWords) {
            List<String> lowerWords = new ArrayList<>();
            List<Pattern> wordPatterns = new ArrayList<>();
            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }
                String lowerWord = word.toLowerCase();
                lowerWords.add(lowerWord);
                if (wholeWords) {
                    wordPatterns.add(wholeWordPattern(lowerWord));
                }
            }

            List<Pattern> compiledPatterns = new ArrayList<>();
            for (String pattern : patterns) {
                try {
                    compiledPatterns.add(Pattern.compile(pattern, Pattern.CASE_INSENSITIVE));
                } catch (PatternSyntaxException e) {
                    // Log invalid regex pattern
                    System.err.println("Invalid regex pattern in banned patterns: " + pattern);
                }
            }

            return new CompiledRules(List.copyOf(words), List.copyOf(patterns), wholeWords,
                    List.copyOf(lowerWords), List.copyOf(wordPatterns), List.copyOf(compiledPatterns));
        }

        boolean matches(List<String> words, List<String> patterns, boolean wholeWords) {
            return this.wholeWords == wholeWords && sourceWords.equals(words) && sourcePatterns.equals(patterns);
        }
    }
}
