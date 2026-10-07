package mk.ukim.finki.aibotbackend.bot.extraction;

import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Estimates how likely a text is Macedonian, without calling any external service.
 *
 * <p>Macedonian shares the Cyrillic script with Serbian, Bulgarian and Russian, so the score is built
 * from clues that only some of those languages have: the letters ѓ, ќ and ѕ exist only in Macedonian,
 * ђ and ћ only in Serbian, ъ, щ, ю, я and the others only in Bulgarian, Russian or Ukrainian. Short,
 * frequent words like "во", "што" or "ќе" against "је", "съм" or "что" are counted the same way.</p>
 */
@Component
public class HeuristicLanguageDetector implements LanguageDetector {
    private static final int MIN_LETTERS = 3;
    private static final Pattern LINKS_AND_MENTIONS = Pattern.compile("https?://\\S+|www\\.\\S+|@\\w+");

    private static final String MACEDONIAN_ONLY_LETTERS = "ѓќѕ";
    private static final String SHARED_WITH_SERBIAN_LETTERS = "јљњџ";
    private static final String NOT_MACEDONIAN_LETTERS = "ђћъщыэюяёйьіїєґў";

    private static final Set<String> MACEDONIAN_WORDS = Set.of(
        "во", "што", "ќе", "со", "сум", "многу", "уште", "оваа", "тоа", "тие", "ние", "вие", "јас",
        "денес", "денеска", "сите", "секој", "нешто", "ништо", "бидејќи", "затоа", "меѓу", "треба",
        "тука", "таму", "зошто", "колку", "овој", "овие", "веќе"
    );
    private static final Set<String> NOT_MACEDONIAN_WORDS = Set.of(
        // Serbian
        "је", "сам", "смо", "још", "када", "кад", "али", "шта", "због", "овај", "ово", "тако", "веома", "у", "из",
        // Bulgarian
        "съм", "със", "това", "още", "ще", "във", "които", "който", "защото", "много",
        // Russian
        "это", "что", "как", "который", "его", "очень"
    );

    @Override
    public double macedonianConfidence(String text) {
        if (text == null || text.isBlank()) {
            return 0.0;
        }
        String cleaned = LINKS_AND_MENTIONS.matcher(text.toLowerCase()).replaceAll(" ");

        int letters = 0;
        int cyrillicLetters = 0;
        int evidenceFor = 0;
        int evidenceAgainst = 0;
        for (char c : cleaned.toCharArray()) {
            if (!Character.isLetter(c)) {
                continue;
            }
            letters++;
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.CYRILLIC) {
                cyrillicLetters++;
            }
            if (MACEDONIAN_ONLY_LETTERS.indexOf(c) >= 0) {
                evidenceFor += 3;
            } else if (SHARED_WITH_SERBIAN_LETTERS.indexOf(c) >= 0) {
                evidenceFor += 1;
            } else if (NOT_MACEDONIAN_LETTERS.indexOf(c) >= 0) {
                evidenceAgainst += 3;
            }
        }
        if (letters < MIN_LETTERS || cyrillicLetters == 0) {
            return 0.0;
        }

        for (String word : cleaned.split("[^\\p{IsCyrillic}]+")) {
            if (MACEDONIAN_WORDS.contains(word)) {
                evidenceFor += 2;
            } else if (NOT_MACEDONIAN_WORDS.contains(word)) {
                evidenceAgainst += 2;
            }
        }

        double cyrillicShare = (double) cyrillicLetters / letters;
        // without any clue this is 0.5, every clue moves it towards 0 or 1
        double evidence = (evidenceFor + 1.0) / (evidenceFor + evidenceAgainst + 2.0);
        return Math.round(cyrillicShare * evidence * 100) / 100.0;
    }
}
